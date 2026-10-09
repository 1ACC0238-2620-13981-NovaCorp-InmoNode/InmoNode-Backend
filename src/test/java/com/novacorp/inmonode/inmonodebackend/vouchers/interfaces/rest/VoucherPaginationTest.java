package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.configuration.WebSecurityConfiguration;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.queries.GetMyVouchersQuery;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.VoucherCommandService;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.VoucherQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VouchersController.class)
@Import(WebSecurityConfiguration.class)
@ActiveProfiles("test")
class VoucherPaginationTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private VoucherCommandService commandService;
    @MockitoBean private VoucherQueryService queryService;
    @MockitoBean private TokenService tokenService;

    @Test
    @WithMockUser(roles = "BUYER")
    void defaultPageReturnsTheTotalCountHeaderAndDoesNotCachePrivateData() throws Exception {
        when(queryService.handle(any())).thenReturn(Result.success(new PageResult<Voucher>(List.of(), 300, 1, 20)));
        mvc.perform(get("/api/v1/vouchers")).andExpect(status().isOk())
                .andExpect(header().string("Total-Count", "300"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.limit").value(20));
        verify(queryService).handle(new GetMyVouchersQuery(new PageRequest(1, 20)));
    }

    @Test
    @WithMockUser(roles = "FIELD_AGENT")
    void oversizedLimitIsClampedBeforeItReachesPersistence() throws Exception {
        when(queryService.handle(any())).thenReturn(Result.success(new PageResult<Voucher>(List.of(), 300, 2, 100)));
        mvc.perform(get("/api/v1/vouchers").param("page", "2").param("limit", "5000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(100));
        verify(queryService).handle(new GetMyVouchersQuery(new PageRequest(2, 100)));
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void malformedAndNonPositiveParametersReturn400() throws Exception {
        mvc.perform(get("/api/v1/vouchers").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/vouchers").param("limit", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/vouchers").param("limit", "abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(queryService);
    }

    @Test
    void anonymousRequestsCannotReadVouchers() throws Exception {
        mvc.perform(get("/api/v1/vouchers")).andExpect(status().isUnauthorized());
        verifyNoInteractions(queryService);
    }

    @Test
    @WithMockUser(roles = "CATALOG_ADMIN")
    void catalogAdministratorsCannotReadPrivateBuyerReceipts() throws Exception {
        mvc.perform(get("/api/v1/vouchers")).andExpect(status().isForbidden());
        verifyNoInteractions(queryService);
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void failuresPreserveTheExistingErrorFormatWithoutAPaginationHeader() throws Exception {
        when(queryService.handle(any())).thenReturn(Result.failure(new ApplicationError("UNAUTHORIZED", "No user")));
        mvc.perform(get("/api/v1/vouchers")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Total-Count"));
    }
}
