package com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.eventhandlers;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.OperationChannel;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.ReservationOperationRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.repositories.ReservationOperationJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Lote separado" reaching Gestión de Comprobantes: a real field synchronization over HTTP turns each reservation that
 * took its lot into a reservation operation, the one its voucher will belong to. Each test uses its own agent and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FieldLotReservedEventHandlerIntegrationTest {

    private static final AtomicLong AGENTS = new AtomicLong(3_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ReservationOperationRepository operationRepository;

    @Autowired
    private ReservationOperationJpaRepository operationJpaRepository;

    @Test
    void aReservationThatTookItsLotBecomesAnOperationOfItsAgent() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var lotId = availableLot();
        var taken = UUID.randomUUID();
        var conflicting = UUID.randomUUID();

        var response = sync(agent, payload(taken, conflicting, lotId));

        var operation = operationRepository.findByReservationId(taken).orElseThrow();
        assertNotNull(operation.getId());
        assertEquals(OperationChannel.FIELD, operation.getChannel());
        assertEquals(agent, operation.getOwnerId());
        assertTrue(operation.isOwnedBy(agent));
        assertEquals(lotId, operation.getLotId());
        assertEquals(0, new BigDecimal("1500").compareTo(operation.getInitialAmount()));
        assertEquals(Instant.parse("2026-10-08T09:30:00Z"), operation.getReservedAt());
        assertEquals(Instant.parse(JsonPath.read(response, "$.reservations[0].blockedUntil")),
                operation.getEvidenceDueAt(), "the voucher is due when the lot block ends");
        assertTrue(operationRepository.findByReservationId(conflicting).isEmpty(),
                "a reservation that lost its lot accepts no voucher");
    }

    @Test
    void reSendingTheSyncKeepsASingleOperation() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var lotId = availableLot();
        var taken = UUID.randomUUID();
        var body = payload(taken, UUID.randomUUID(), lotId);
        sync(agent, body);
        var first = operationRepository.findByReservationId(taken).orElseThrow();

        sync(agent, body);

        var afterReSend = operationRepository.findByReservationId(taken).orElseThrow();
        assertEquals(first.getId(), afterReSend.getId());
        assertEquals(first.getEvidenceDueAt(), afterReSend.getEvidenceDueAt());
    }

    @Test
    void aReSendRecordsTheOperationTheFirstDeliveryMissed() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var lotId = availableLot();
        var taken = UUID.randomUUID();
        var body = payload(taken, UUID.randomUUID(), lotId);
        sync(agent, body);
        operationJpaRepository.findByReservationId(taken).ifPresent(operationJpaRepository::delete);

        sync(agent, body);

        var operation = operationRepository.findByReservationId(taken).orElseThrow();
        assertEquals(agent, operation.getOwnerId());
        assertEquals(lotId, operation.getLotId());
        assertNotNull(operation.getEvidenceDueAt(), "the lot is still held, so the deadline is known");
    }

    private String sync(long agent, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/field-sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservations[1].reservationStatus").value("CANCELLED_BY_CONFLICT"))
                .andReturn().getResponse().getContentAsString();
    }

    /** One prospect and two reservations of the same lot: the first takes it, the second conflicts. */
    private static String payload(UUID taken, UUID conflicting, Long lotId) {
        var prospect = UUID.randomUUID();
        return """
                {"prospects": [{"id": "%1$s", "document": "12345678", "fullName": "Ana Quispe",
                                "registeredAt": "2026-10-08T09:00:00Z"}],
                 "reservations": [
                   {"id": "%2$s", "lotId": %4$d, "prospectId": "%1$s", "initialAmount": 1500,
                    "reservedAt": "2026-10-08T09:30:00Z"},
                   {"id": "%3$s", "lotId": %4$d, "prospectId": "%1$s", "initialAmount": 1500,
                    "reservedAt": "2026-10-08T09:30:00Z"}]}""".formatted(prospect, taken, conflicting, lotId);
    }

    private Long availableLot() {
        var rules = new FinancingRules(BigDecimal.TEN, BigDecimal.TEN, 60, BigDecimal.ONE);
        var project = projectRepository.save(Project.create("Comprobantes", "Chilca", null, null, rules));
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        var lot = Lot.register(project.getId(), "V-01", new LotDimensions(new BigDecimal("120"), null, null),
                Money.of(new BigDecimal("45000")), boundary);
        return lotRepository.saveAll(List.of(lot)).getFirst().getId();
    }

    private String bearer(long agent) {
        var user = User.restore(agent, "agent" + agent + "@mail.com", "hash", Role.FIELD_AGENT, UserStatus.ACTIVE,
                null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
