package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.services.CoOwnerCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AddCoOwnerCommand;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.QuotationDocumentQueryService;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.queries.DownloadQuotationQuery;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.QuotationDocumentsController;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.configuration.WebSecurityConfiguration;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CoOwnersController.class, QuotationDocumentsController.class})
@Import(WebSecurityConfiguration.class)
@ActiveProfiles("test")
class CoOwnerAndQuotationDocumentsTest {
    @Autowired MockMvc mvc;
    @MockitoBean CoOwnerCommandService coOwners;
    @MockitoBean QuotationDocumentQueryService documents;
    @MockitoBean TokenService tokens;

    @Test @WithMockUser(roles = "BUYER")
    void coOwnerIsValidatedBeforeInvokingTheService() throws Exception {
        mvc.perform(post("/api/v1/reservations/{id}/co-owner", UUID.randomUUID()).contentType("application/json")
                .content("{\"fullName\":\"María Pérez\",\"documentType\":\"DNI\",\"documentNumber\":\"123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(coOwners);
    }

    @Test @WithMockUser(roles = "BUYER")
    void aPostIssuanceChangeReturnsConflictWithTheLegalAddendumMessage() throws Exception {
        when(coOwners.handle(any(AddCoOwnerCommand.class))).thenReturn(Result.failure(ApplicationError.conflict("co-owner", "legal addendum required")));
        mvc.perform(post("/api/v1/reservations/{id}/co-owner", UUID.randomUUID()).contentType("application/json")
                .content("{\"fullName\":\"María Pérez\",\"documentType\":\"DNI\",\"documentNumber\":\"12345678\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details").value("legal addendum required"));
    }

    @Test @WithMockUser(roles = "BUYER")
    void pdfDownloadsHaveTheCorrectTypeFilenameAndPrivateCachePolicy() throws Exception {
        when(documents.handle(new DownloadQuotationQuery(1L))).thenReturn(Result.success("%PDF-test".getBytes()));
        mvc.perform(get("/api/v1/quotations/1/download")).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"quotation-1.pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test @WithMockUser(roles = "BUYER")
    void errorsAreJsonAndNeverPresentedAsAPdfDownload() throws Exception {
        when(documents.handle(new DownloadQuotationQuery(9L))).thenReturn(Result.failure(ApplicationError.notFound("quotation", "9")));
        mvc.perform(get("/api/v1/quotations/9/download")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTATION_NOT_FOUND"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test void downloadsWithoutAUserAreUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/quotations/1/download")).andExpect(status().isUnauthorized());
        verifyNoInteractions(documents);
    }

    @Test @WithMockUser(roles = "FINANCE_ADMIN")
    void financeCannotModifyABuyersCoOwnerOrReadTheirQuotation() throws Exception {
        mvc.perform(get("/api/v1/quotations/1/download")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reservations/{id}/co-owner", UUID.randomUUID()).contentType("application/json")
                .content("{\"fullName\":\"María Pérez\",\"documentType\":\"DNI\",\"documentNumber\":\"12345678\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documents, coOwners);
    }
}
