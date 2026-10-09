package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.services.*;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.configuration.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RealEstateCatalogController.class)
@Import(WebSecurityConfiguration.class)
@ActiveProfiles("test")
class IndividualCatalogAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProjectCommandService projects;
    @MockitoBean ProjectQueryService queries;
    @MockitoBean LotCommandService lots;
    @MockitoBean TokenService tokens;
    private final String rules = "\"financingRules\":{\"minDownPaymentPercentage\":20,\"annualInterestRate\":12,\"maxTermMonths\":120,\"lateFeeRate\":1.5}";

    @Test @WithMockUser(roles = "CATALOG_ADMIN")
    void missingAndDuplicateStagesAreBadRequestsBeforeWriting() throws Exception {
        mvc.perform(post("/api/v1/catalog/projects").contentType("application/json")
                .content("{\"name\":\"Proyecto\",\"location\":\"Lima\"," + rules + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/catalog/projects").contentType("application/json")
                .content("{\"name\":\"Proyecto\",\"location\":\"Lima\",\"stages\":[\"A\",\"a\"]," + rules + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(projects);
    }

    @Test @WithMockUser(roles = "CATALOG_ADMIN")
    void crossingPolygonsCannotCreateALot() throws Exception {
        mvc.perform(post("/api/v1/catalog/projects/1/lots").contentType("application/json")
                .content("""
                    {"code":"A-01","stageName":"Norte","area":120,"price":45000,
                     "polygon":[[[-76.7,-12.5],[-76.6,-12.4],[-76.7,-12.4],[-76.6,-12.5],[-76.7,-12.5]]]}
                    """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(lots);
    }

    @Test @WithMockUser(roles = "BUYER")
    void aBuyerCannotPublishOrReadDraftInventory() throws Exception {
        mvc.perform(put("/api/v1/catalog/lots/1/publish")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/catalog/projects/1/lots")).andExpect(status().isForbidden());
        verifyNoInteractions(lots, queries);
    }
}
