package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Project registration over HTTP against a real PostgreSQL, with the role rules of the back-office.
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

    private ResultActions createProject(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpServletRequestBuilder as(Role role) {
        var user = User.restore(1L, role.name().toLowerCase() + "@mail.com", "hash", role, UserStatus.ACTIVE,
                null, null, 0, null);
        return post("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.generateToken(user));
    }
}
