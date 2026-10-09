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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Project registration and publication over HTTP against a real PostgreSQL, with the role rules of the back-office.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProjectsControllerIntegrationTest {

    private static final String VALID_PROJECT = """
            {
              "name": "Los Pinos de Chilca",
              "location": "Chilca, Cañete, Lima",
              "latitude": -12.5213,
              "longitude": -76.7372,
              "coverImageUrl": "https://cdn.inmonode.dev/los-pinos.jpg",
              "financingRules": {
                "minDownPaymentPercentage": 10,
                "annualInterestRate": 12.5,
                "maxTermMonths": 120,
                "lateFeeRate": 2
              }
            }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Test
    void catalogAdminRegistersADraftProject() throws Exception {
        createProject(as(Role.CATALOG_ADMIN), VALID_PROJECT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Los Pinos de Chilca"))
                .andExpect(jsonPath("$.location").value("Chilca, Cañete, Lima"))
                .andExpect(jsonPath("$.latitude").value(-12.5213))
                .andExpect(jsonPath("$.longitude").value(-76.7372))
                .andExpect(jsonPath("$.financingRules.minDownPaymentPercentage").value(10.0))
                .andExpect(jsonPath("$.financingRules.annualInterestRate").value(12.5))
                .andExpect(jsonPath("$.financingRules.maxTermMonths").value(120))
                .andExpect(jsonPath("$.financingRules.lateFeeRate").value(2.0))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void coordinatesAndCoverImageAreOptional() throws Exception {
        createProject(as(Role.CATALOG_ADMIN), """
                {"name": "Villa Sol", "location": "Huaral, Lima",
                 "financingRules": {"minDownPaymentPercentage": 20, "annualInterestRate": 0,
                                    "maxTermMonths": 36, "lateFeeRate": 1.5}}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.coverImageUrl").doesNotExist())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void onlyTheCatalogAdminCanRegisterProjects() throws Exception {
        createProject(as(Role.BUYER), VALID_PROJECT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        createProject(as(Role.FIELD_AGENT), VALID_PROJECT).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content(VALID_PROJECT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidProjectsAreRejected() throws Exception {
        createProject(as(Role.CATALOG_ADMIN), """
                {"name": " ", "location": "Chilca",
                 "financingRules": {"minDownPaymentPercentage": 150, "annualInterestRate": 12,
                                    "maxTermMonths": 120, "lateFeeRate": 2}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        createProject(as(Role.CATALOG_ADMIN), """
                {"name": "Los Pinos", "location": "Chilca", "latitude": -12.5,
                 "financingRules": {"minDownPaymentPercentage": 10, "annualInterestRate": 12,
                                    "maxTermMonths": 120, "lateFeeRate": 2}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        createProject(as(Role.CATALOG_ADMIN), """
                {"name": "Los Pinos", "location": "Chilca"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void projectIsPublishedOnlyOnceItHasLots() throws Exception {
        var projectId = idOf(createProject(as(Role.CATALOG_ADMIN), VALID_PROJECT).andExpect(status().isCreated()));

        publish(projectId, Role.CATALOG_ADMIN)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.details").value(containsString("at least one lot")));

        mockMvc.perform(post("/api/v1/projects/{projectId}/lots", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ClassPathResource("fixtures/plan-los-pinos.geojson")
                                .getContentAsString(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        publish(projectId, Role.CATALOG_ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        publish(projectId, Role.CATALOG_ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void onlyTheCatalogAdminPublishesAndTheProjectMustExist() throws Exception {
        var projectId = idOf(createProject(as(Role.CATALOG_ADMIN), VALID_PROJECT).andExpect(status().isCreated()));

        publish(projectId, Role.BUYER).andExpect(status().isForbidden());
        publish(999_999L, Role.CATALOG_ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    private ResultActions createProject(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions publish(Long projectId, Role role) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/{projectId}/publish", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(role)));
    }

    private MockHttpServletRequestBuilder as(Role role) {
        return post("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, bearer(role));
    }

    private String bearer(Role role) {
        var user = User.restore(1L, role.name().toLowerCase() + "@mail.com", "hash", role, UserStatus.ACTIVE,
                null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }

    private static Long idOf(ResultActions response) throws Exception {
        return ((Number) JsonPath.read(response.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
