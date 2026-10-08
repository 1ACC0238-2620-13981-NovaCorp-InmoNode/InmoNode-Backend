package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
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
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Portfolio download for the field app (US-02) and its ETag revalidation (US-39) against a real PostgreSQL.
 * Other tests share the database, so projects are looked up by id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FieldSyncControllerIntegrationTest {

    private static final String PORTFOLIO = "/api/v1/field-sync/portfolio";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Test
    void fieldAgentDownloadsPublishedProjectsWithFinancingRulesAndLots() throws Exception {
        var publishedId = createProject("Los Pinos");
        importPlanLots(publishedId);
        publish(publishedId);
        var draftId = createProject("Borrador");
        importPlanLots(draftId);

        var body = portfolio(Role.FIELD_AGENT)
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse().getContentAsString();

        var project = project(body, publishedId);
        assertNotNull(project, "the published project is in the portfolio");
        Integer maxTermMonths = JsonPath.read(project, "$.financingRules.maxTermMonths");
        String lotsType = JsonPath.read(project, "$.lots.type");
        List<String> lotCodes = JsonPath.read(project, "$.lots.features[*].properties.code");
        String geometryType = JsonPath.read(project, "$.lots.features[0].geometry.type");
        assertEquals("Los Pinos", project.get("name"));
        assertEquals(120, maxTermMonths);
        assertEquals("FeatureCollection", lotsType);
        assertEquals(List.of("A-01", "A-02", "A-03"), lotCodes);
        assertEquals("Polygon", geometryType);
        assertNull(project(body, draftId), "drafts are not downloaded");
    }

    @Test
    void unchangedPortfolioIsRevalidatedWithItsEtag() throws Exception {
        var projectId = createProject("Villa Sol");
        importPlanLots(projectId);
        publish(projectId);
        var etag = portfolio(Role.FIELD_AGENT).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(etag);

        mockMvc.perform(get(PORTFOLIO).header(HttpHeaders.AUTHORIZATION, bearer(Role.FIELD_AGENT))
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));

        var otherId = createProject("Las Lomas");
        importPlanLots(otherId);
        publish(otherId);
        mockMvc.perform(get(PORTFOLIO).header(HttpHeaders.AUTHORIZATION, bearer(Role.FIELD_AGENT))
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, not(etag)));
    }

    @Test
    void onlyFieldAgentsDownloadThePortfolio() throws Exception {
        portfolio(Role.BUYER).andExpect(status().isForbidden());
        portfolio(Role.CATALOG_ADMIN).andExpect(status().isForbidden());
        mockMvc.perform(get(PORTFOLIO)).andExpect(status().isUnauthorized());
    }

    private ResultActions portfolio(Role role) throws Exception {
        return mockMvc.perform(get(PORTFOLIO).header(HttpHeaders.AUTHORIZATION, bearer(role)));
    }

    private static Map<String, Object> project(String portfolio, Long projectId) {
        List<Map<String, Object>> matches = JsonPath.read(portfolio, "$.projects[?(@.id == %d)]".formatted(projectId));
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private Long createProject(String name) throws Exception {
        var response = mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "location": "Chilca, Lima",
                                 "financingRules": {"minDownPaymentPercentage": 10, "annualInterestRate": 12,
                                                    "maxTermMonths": 120, "lateFeeRate": 2}}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void importPlanLots(Long projectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{id}/lots", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ClassPathResource("fixtures/plan-los-pinos.geojson")
                                .getContentAsString(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(3));
    }

    private void publish(Long projectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{id}/publish", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN)))
                .andExpect(status().isOk());
    }

    private String bearer(Role role) {
        var user = User.restore(1L, role.name().toLowerCase() + "@mail.com", "hash", role, UserStatus.ACTIVE,
                null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
