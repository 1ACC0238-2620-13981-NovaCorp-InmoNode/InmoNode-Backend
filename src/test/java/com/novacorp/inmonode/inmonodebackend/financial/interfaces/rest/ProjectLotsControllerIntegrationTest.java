package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Loading a project plan over HTTP against a real PostgreSQL (US-53, Scenario 2): invalid lots are rejected
 * one by one and the valid ones are saved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProjectLotsControllerIntegrationTest {

    private static final String GEO_JSON = "application/geo+json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private LotJpaRepository lotJpaRepository;

    @Test
    void validLotsAreSavedAndEachInvalidOneIsRejectedWithItsReason() throws Exception {
        var projectId = createProject();

        importLots(projectId, Role.CATALOG_ADMIN, MediaType.APPLICATION_JSON_VALUE, fixture("plan-los-pinos.geojson"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.rejected.length()").value(4))
                .andExpect(jsonPath("$.rejected[0].index").value(3))
                .andExpect(jsonPath("$.rejected[0].code").value("a-01"))
                .andExpect(jsonPath("$.rejected[0].reason").value(containsString("repeated")))
                .andExpect(jsonPath("$.rejected[1].code").value("A-05"))
                .andExpect(jsonPath("$.rejected[1].reason").value(containsString("crosses itself")))
                .andExpect(jsonPath("$.rejected[2].code").value("A-06"))
                .andExpect(jsonPath("$.rejected[2].reason").value(containsString("price")))
                .andExpect(jsonPath("$.rejected[3].code").value("A-07"))
                .andExpect(jsonPath("$.rejected[3].reason").value(containsString("Polygon")));

        var saved = lotJpaRepository.findAll().stream().filter(lot -> lot.getProjectId().equals(projectId)).toList();
        assertEquals(3, saved.size());
        var first = saved.stream().filter(lot -> lot.getCode().equals("A-01")).findFirst().orElseThrow();
        assertEquals("AVAILABLE", first.getStatus().name());
        assertEquals("PEN", first.getPriceCurrency());
        assertTrue(first.getBoundaryWkt().startsWith("POLYGON"));
    }

    @Test
    void codesAlreadyUsedInTheProjectAreRejectedOnALaterImport() throws Exception {
        var projectId = createProject();
        importLots(projectId, Role.CATALOG_ADMIN, GEO_JSON, fixture("plan-los-pinos.geojson")).andExpect(status().isOk());

        importLots(projectId, Role.CATALOG_ADMIN, GEO_JSON, """
                {"type": "FeatureCollection", "features": [
                  {"type": "Feature", "properties": {"code": "A-02", "area": 118, "price": 44000},
                   "geometry": {"type": "Polygon", "coordinates": [[[0, 0], [0.001, 0], [0.001, 0.001], [0, 0]]]}},
                  {"type": "Feature", "properties": {"code": "B-01", "area": 200, "price": 70000},
                   "geometry": {"type": "Polygon", "coordinates": [[[0, 0], [0.001, 0], [0.001, 0.001], [0, 0]]]}}
                ]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.rejected[0].code").value("A-02"))
                .andExpect(jsonPath("$.rejected[0].reason").value(containsString("already used")));
    }

    @Test
    void onlyTheCatalogAdminCanImportAndTheProjectMustExist() throws Exception {
        var projectId = createProject();
        var plan = fixture("plan-los-pinos.geojson");

        importLots(projectId, Role.BUYER, GEO_JSON, plan).andExpect(status().isForbidden());
        importLots(999_999L, Role.CATALOG_ADMIN, GEO_JSON, plan)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void malformedPlansAreRejectedAsAWhole() throws Exception {
        var projectId = createProject();

        importLots(projectId, Role.CATALOG_ADMIN, GEO_JSON, "{\"type\": \"FeatureCollection\", \"features\": [")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        importLots(projectId, Role.CATALOG_ADMIN, GEO_JSON, "{\"type\": \"Feature\", \"features\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/projects/abc/lots").header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN))
                        .contentType(GEO_JSON).content(fixture("plan-los-pinos.geojson")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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

    private ResultActions importLots(Long projectId, Role role, String contentType, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/{projectId}/lots", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(role))
                .contentType(contentType)
                .content(body));
    }

    private String bearer(Role role) {
        var user = User.restore(1L, role.name().toLowerCase() + "@mail.com", "hash", role, UserStatus.ACTIVE,
                null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }

    private static String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
