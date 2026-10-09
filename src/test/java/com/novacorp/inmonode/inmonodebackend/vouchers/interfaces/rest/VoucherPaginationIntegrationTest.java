package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.ReservationOperation;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.ExtractedData;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherFile;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.ReservationOperationRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PostgreSQL validates the ownership query, count, sort and offset, without contacting S3. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class VoucherPaginationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private TokenService tokenService;
    @Autowired private VoucherRepository vouchers;
    @Autowired private ReservationOperationRepository operations;

    @Test
    void threeHundredReceiptsArePagedAndCountedOnlyForTheirOwner() throws Exception {
        var owner = 9_460_001L;
        var other = 9_460_002L;
        seed(owner, 300);
        seed(other, 5);

        mvc.perform(get("/api/v1/vouchers").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("page", "1").param("limit", "20"))
                .andExpect(status().isOk()).andExpect(header().string("Total-Count", "300"))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].operationCode").value("OP-299"))
                .andExpect(jsonPath("$.items[19].operationCode").value("OP-280"));
        mvc.perform(get("/api/v1/vouchers").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("page", "2").param("limit", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].operationCode").value("OP-279"));
        mvc.perform(get("/api/v1/vouchers").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("limit", "5000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(100))
                .andExpect(jsonPath("$.limit").value(100));
        mvc.perform(get("/api/v1/vouchers").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("page", "16").param("limit", "20"))
                .andExpect(status().isOk()).andExpect(header().string("Total-Count", "300"))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/vouchers").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk()).andExpect(header().string("Total-Count", "5"))
                .andExpect(jsonPath("$.items.length()").value(5));
    }

    @Test
    void equalReceiptTimesUseIdsForStablePagination() throws Exception {
        var owner = 9_460_003L;
        var reservationId = seedOperation(owner);
        var time = Instant.parse("2026-01-01T12:00:00Z");
        seedVoucher(reservationId, "FIRST", time);
        seedVoucher(reservationId, "SECOND", time);
        mvc.perform(get("/api/v1/vouchers").header(HttpHeaders.AUTHORIZATION, bearer(owner)).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Total-Count", "2"))
                .andExpect(jsonPath("$.items[0].operationCode").value("SECOND"));
        mvc.perform(get("/api/v1/vouchers").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("limit", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].operationCode").value("FIRST"));
    }

    private void seed(Long owner, int count) {
        var reservationId = seedOperation(owner);
        var time = Instant.parse("2026-01-01T12:00:00Z");
        for (var i = 0; i < count; i++) seedVoucher(reservationId, "OP-" + i, time.plusSeconds(i));
    }

    private UUID seedOperation(Long owner) {
        var reservationId = UUID.randomUUID();
        operations.save(ReservationOperation.fromFieldReservation(reservationId, owner, 1L,
                new BigDecimal("1000"), Instant.parse("2026-01-01T00:00:00Z"), null));
        return reservationId;
    }

    private void seedVoucher(UUID reservationId, String code, Instant time) {
        vouchers.save(Voucher.receive(VoucherFile.of(reservationId, UUID.randomUUID(), "application/pdf", 100),
                new ExtractedData(new BigDecimal("100"), "PEN", LocalDate.parse("2026-01-01"), code, null),
                false, time));
    }

    private String bearer(Long userId) {
        return "Bearer " + tokenService.generateToken(User.restore(userId, "history@mail.com", "hash", Role.BUYER,
                UserStatus.ACTIVE, null, null, 0, null));
    }
}
