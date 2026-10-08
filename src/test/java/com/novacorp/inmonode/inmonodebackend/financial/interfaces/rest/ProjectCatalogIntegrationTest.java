package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public project catalog (US-15) and the lots map (US-05) over HTTP against a real PostgreSQL.
 * Other tests share the database, so catalog entries are looked up by id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProjectCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private LotJpaRepository lotJpaRepository;

    @Test
    void publishedProjectIsListedWithoutSessionWithItsPriceRangeAndAvailability() throws Exception {
        var projectId = publishedProjectWithPlanLots();

        var entry = catalogEntry(projectId);

        assertEquals("Chilca, Lima", entry.get("location"));
        assertEquals(Map.of("min", 44000.5, "max", 47500.0, "currency", "PEN"), entry.get("priceRange"));
        assertEquals(3, entry.get("totalLots"));
        assertEquals(100.0, entry.get("availabilityPercentage"));
        assertEquals(false, entry.get("soldOut"));
    }

    @Test
    void availabilityFollowsTheLotsAndASoldOutProjectStaysListed() throws Exception {
        var projectId = publishedProjectWithPlanLots();

        markSold(projectId, Set.of("A-01"));
        assertEquals(66.67, catalogEntry(projectId).get("availabilityPercentage"));
        assertEquals(false, catalogEntry(projectId).get("soldOut"));

        markSold(projectId, Set.of("A-02", "A-03"));
        var entry = catalogEntry(projectId);
        assertEquals(0.0, entry.get("availabilityPercentage"));
        assertEquals(true, entry.get("soldOut"));
    }

    @Test
    void draftsAreHiddenFromEveryoneButTheCatalogAdmin() throws Exception {
        var draftId = createProject();
        importPlanLots(draftId);

        assertNull(findCatalogEntry(draftId));
        mockMvc.perform(get("/api/v1/projects/{id}", draftId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/projects/{id}", draftId).header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/{id}/lots", draftId)).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects/{id}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(get("/api/v1/projects/{id}/lots", draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(3));
    }

    @Test
    void publishedProjectDetailShowsItsFinancingRules() throws Exception {
        var projectId = publishedProjectWithPlanLots();

        mockMvc.perform(get("/api/v1/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.financingRules.minDownPaymentPercentage").value(10.0))
                .andExpect(jsonPath("$.financingRules.maxTermMonths").value(120));
    }

    @Test
    void lotsAreServedAsAGeoJsonFeatureCollectionOrderedByCode() throws Exception {
        var projectId = publishedProjectWithPlanLots();

        mockMvc.perform(get("/api/v1/projects/{id}/lots", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(3))
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].id").isNumber())
                .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0].length()").value(5))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0][0][0]").value(-76.737))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0][0][1]").value(-12.521))
                .andExpect(jsonPath("$.features[0].properties.code").value("A-01"))
                .andExpect(jsonPath("$.features[0].properties.area").value(120.5))
                .andExpect(jsonPath("$.features[0].properties.front").value(8.0))
                .andExpect(jsonPath("$.features[0].properties.price").value(45000.0))
                .andExpect(jsonPath("$.features[0].properties.currency").value("PEN"))
                .andExpect(jsonPath("$.features[0].properties.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.features[1].properties.code").value("A-02"))
                .andExpect(jsonPath("$.features[2].properties.code").value("A-03"));
    }

    @Test
    void unknownProjectsAreNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}", 999_999L)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/{id}/lots", 999_999L)).andExpect(status().isNotFound());
    }

    private Long publishedProjectWithPlanLots() throws Exception {
        var projectId = createProject();
        importPlanLots(projectId);
        mockMvc.perform(post("/api/v1/projects/{id}/publish", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN)))
                .andExpect(status().isOk());
        return projectId;
    }

    private Long createProject() throws Exception {
        var response = mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Los Pinos", "location": "Chilca, Lima",
                                 "financingRules": {"minDownPaymentPercentage": 10, "annualInterestRate": 12,
                                                    "maxTermMonths": 120, "lateFeeRate": 2}}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    /** Loads the 3 valid lots of the fixture plan (A-01 to A-03); its invalid lots are rejected. */
    private void importPlanLots(Long projectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{id}/lots", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ClassPathResource("fixtures/plan-los-pinos.geojson")
                                .getContentAsString(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(3));
    }

    private void markSold(Long projectId, Set<String> codes) {
        var lots = lotJpaRepository.findByProjectIdOrderByCodeAsc(projectId).stream()
                .filter(lot -> codes.contains(lot.getCode()))
                .toList();
        lots.forEach(lot -> lot.setStatus(LotStatus.SOLD));
        lotJpaRepository.saveAll(lots);
    }

    private Map<String, Object> catalogEntry(Long projectId) throws Exception {
        var entry = findCatalogEntry(projectId);
        assertNotNull(entry, "project %d is not in the public catalog".formatted(projectId));
        return entry;
    }

    private Map<String, Object> findCatalogEntry(Long projectId) throws Exception {
        var response = mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> entries = JsonPath.read(response, "$[?(@.id == %d)]".formatted(projectId));
        return entries.isEmpty() ? null : entries.getFirst();
    }

    private String bearer(Role role) {
        var user = User.restore(1L, role.name().toLowerCase() + "@mail.com", "hash", role, UserStatus.ACTIVE,
                null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
