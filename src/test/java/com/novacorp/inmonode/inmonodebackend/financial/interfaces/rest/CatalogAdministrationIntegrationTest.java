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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalog back-office over HTTP against a real PostgreSQL: a catalog administrator registers a project with its
 * stages, registers its lots and publishes both, and the buyer catalog shows the lot only once it is published.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CatalogAdministrationIntegrationTest {

    private static final String SQUARE = "[[[-76.7372,-12.5213],[-76.7370,-12.5213],[-76.7370,-12.5211],[-76.7372,-12.5211],[-76.7372,-12.5213]]]";
    private static final String BOW_TIE = "[[[-76.7,-12.5],[-76.6,-12.4],[-76.7,-12.4],[-76.6,-12.5],[-76.7,-12.5]]]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Test
    void catalogAdminRegistersADraftProjectWithItsStages() throws Exception {
        createProject("Los Pinos de Chilca", "Chilca, Cañete, Lima")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Los Pinos de Chilca"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[0]").value("Etapa Norte"))
                .andExpect(jsonPath("$.stages[1]").value("Etapa Sur"))
                .andExpect(jsonPath("$.financingRules.minDownPaymentPercentage").value(10.0))
                .andExpect(jsonPath("$.financingRules.annualInterestRate").value(12.5))
                .andExpect(jsonPath("$.financingRules.maxTermMonths").value(60));
    }

    @Test
    void projectWithoutLocationIsRejected() throws Exception {
        createProject("Sin ubicación", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void lotIsRegisteredAsADraftInTheStageOfTheProject() throws Exception {
        var projectId = idOf(createProject(uniqueName(), "Chilca, Lima"));

        registerLot(projectId, " n-01 ", "etapa norte", "45000", SQUARE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.code").value("N-01"))
                .andExpect(jsonPath("$.stageName").value("Etapa Norte"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.price").value(45000))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.geometry.type").value("Polygon"))
                .andExpect(jsonPath("$.geometry.coordinates[0].length()").value(5));
        mockMvc.perform(get("/api/v1/catalog/projects/{projectId}/lots", projectId).header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("N-01"));
    }

    @Test
    void lotWithACrossingPolygonIsRejected() throws Exception {
        var projectId = idOf(createProject(uniqueName(), "Chilca, Lima"));

        registerLot(projectId, "N-01", "Etapa Norte", "45000", BOW_TIE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(containsString("crosses itself or has no area")));
    }

    @Test
    void lotCodeCannotRepeatWithinTheProject() throws Exception {
        var projectId = idOf(createProject(uniqueName(), "Chilca, Lima"));
        registerLot(projectId, "N-01", "Etapa Norte", "45000", SQUARE).andExpect(status().isCreated());

        registerLot(projectId, "n-01", "Etapa Sur", "47000", SQUARE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOT_CONFLICT"))
                .andExpect(jsonPath("$.details").value(containsString("already used in this project")));
    }

    @Test
    void lotStageMustBelongToTheProject() throws Exception {
        var projectId = idOf(createProject(uniqueName(), "Chilca, Lima"));

        registerLot(projectId, "N-01", "Etapa Este", "45000", SQUARE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(containsString("stageName does not belong to this project")));
    }

    @Test
    void lotOfAMissingProjectIsNotFound() throws Exception {
        registerLot(999_999L, "N-01", "Etapa Norte", "45000", SQUARE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void projectWithoutLotsCannotBePublished() throws Exception {
        var projectId = idOf(createProject(uniqueName(), "Chilca, Lima"));

        publishProject(projectId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.details").value(containsString("at least one lot")));
    }

    @Test
    void lotCannotBePublishedBeforeItsProject() throws Exception {
        var projectId = idOf(createProject(uniqueName(), "Chilca, Lima"));
        var lotId = idOf(registerLot(projectId, "N-01", "Etapa Norte", "45000", SQUARE));

        publishLot(lotId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.details").value(containsString("Publish the project before publishing its lots")));
    }

    @Test
    void publishedLotReachesTheBuyerCatalog() throws Exception {
        var projectId = idOf(createProject(uniqueName(), "Chilca, Lima"));
        var lotId = idOf(registerLot(projectId, "N-01", "Etapa Norte", "45000", SQUARE));
        registerLot(projectId, "S-01", "Etapa Sur", "58000", SQUARE).andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/projects/{id}", projectId)).andExpect(status().isNotFound());

        publishProject(projectId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/projects/{id}/lots", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(0));

        publishLot(lotId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mockMvc.perform(get("/api/v1/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").isString());
        mockMvc.perform(get("/api/v1/projects/{id}/lots", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].id").value(lotId))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
                .andExpect(jsonPath("$.features[0].properties.code").value("N-01"))
                .andExpect(jsonPath("$.features[0].properties.price").value(45000))
                .andExpect(jsonPath("$.features[0].properties.status").value("AVAILABLE"));
    }

    @Test
    void publishingAMissingLotIsNotFound() throws Exception {
        publishLot(999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOT_NOT_FOUND"));
    }

    @Test
    void onlyTheCatalogAdministratorUsesTheBackOffice() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/projects").header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(projectJson(uniqueName(), "Chilca, Lima")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/catalog/projects")
                        .contentType(MediaType.APPLICATION_JSON).content(projectJson(uniqueName(), "Chilca, Lima")))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions createProject(String name, String location) throws Exception {
        return mockMvc.perform(post("/api/v1/catalog/projects").header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(projectJson(name, location)));
    }

    private ResultActions registerLot(Long projectId, String code, String stageName, String price, String polygon) throws Exception {
        return mockMvc.perform(post("/api/v1/catalog/projects/{projectId}/lots", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s", "stageName": "%s", "area": 120, "front": 8, "depth": 15,
                         "price": %s, "polygon": %s}""".formatted(code, stageName, price, polygon)));
    }

    private ResultActions publishProject(Long projectId) throws Exception {
        return mockMvc.perform(put("/api/v1/catalog/projects/{projectId}/publish", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN)));
    }

    private ResultActions publishLot(Long lotId) throws Exception {
        return mockMvc.perform(put("/api/v1/catalog/lots/{lotId}/publish", lotId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.CATALOG_ADMIN)));
    }

    private static String projectJson(String name, String location) {
        return """
                {"name": "%s", "location": "%s", "latitude": -12.5213, "longitude": -76.7372,
                 "financingRules": {"minDownPaymentPercentage": 10, "annualInterestRate": 12.5,
                                    "maxTermMonths": 60, "lateFeeRate": 2},
                 "stages": ["Etapa Norte", "Etapa Sur"]}""".formatted(name, location);
    }

    private static String uniqueName() {
        return "Proyecto " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String bearer(Role role) {
        var user = User.restore(1L, role.name().toLowerCase() + "@mail.com", "hash", role, UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }

    private static Long idOf(ResultActions response) throws Exception {
        return ((Number) JsonPath.read(response.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
