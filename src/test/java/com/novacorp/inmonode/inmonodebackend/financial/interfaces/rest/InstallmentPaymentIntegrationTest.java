package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.GatewayPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.GatewayPaymentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGateway;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingPlan;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentSource;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe.StripeSignatures.paymentIntentEvent;
import static com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe.StripeSignatures.sign;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-55 end to end over HTTP against a real PostgreSQL: the buyer starts paying an installment, and Stripe's signed
 * webhook credits it. Only the calls to Stripe are replaced; the webhooks are signed with the test-only secret of the
 * test profile and verified for real. Each test uses its own buyer and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class InstallmentPaymentIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_test-only-secret";
    private static final String PAYMENT_INTENTS = "/api/v1/account-statements/{id}/installments/{number}/payment-intents";
    private static final String WEBHOOK = "/api/v1/webhooks/stripe";
    private static final long CENTS = 318_823L;
    private static final AtomicLong BUYERS = new AtomicLong(23_000);
    private static final AtomicInteger CHARGES = new AtomicInteger();

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AccountStatementRepository accountStatementRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void stripeAnswers() {
        when(paymentGateway.isEnabled()).thenReturn(true);
        when(paymentGateway.publishableKey()).thenReturn("pk_test_integration");
        when(paymentGateway.open(any())).thenAnswer(invocation -> {
            InstallmentPaymentIntent intent = invocation.getArgument(0);
            return charge("pi_it_" + CHARGES.incrementAndGet(), GatewayPaymentStatus.PENDING,
                    intent.amountInMinorUnits(), 0);
        });
        when(paymentGateway.retrieve(anyString())).thenAnswer(invocation ->
                charge(invocation.getArgument(0), GatewayPaymentStatus.SUCCEEDED, CENTS, CENTS));
    }

    @Test
    void theBuyerStartsThePaymentAndOnlyTheSignedWebhookCreditsIt() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var statement = statementOf(buyer);

        var body = start(buyer, statement, 2, "attempt-1")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amount").value(3188.23))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.installmentNumber").value(2))
                .andExpect(jsonPath("$.publishableKey").value("pk_test_integration"))
                .andReturn().getResponse().getContentAsString();
        String intentId = JsonPath.read(body, "$.id");
        String providerId = JsonPath.read(body, "$.clientSecret").toString().replace("secret_", "");
        start(buyer, statement, 2, "attempt-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(intentId))
                .andExpect(jsonPath("$.clientSecret").value("secret_" + providerId));
        assertEquals(InstallmentStatus.PENDING, installment(statement, 2).getStatus());

        var payload = succeeded("evt_it_" + UUID.randomUUID(), providerId, intentId);
        webhook(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CREDITED"));

        var paid = installment(statement, 2);
        assertEquals(InstallmentStatus.PAID, paid.getStatus());
        assertEquals(PaymentSource.GATEWAY, paid.getPaymentSource());
        assertEquals(providerId, paid.getPaymentReference());
        assertEquals(new BigDecimal("3188.23"), paid.getPaidAmount());
        mockMvc.perform(get(PAYMENT_INTENTS + "/{intentId}", statement.getId(), 2, intentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.clientSecret").value(nullValue()));
        mockMvc.perform(get("/api/v1/reservations/{transactionId}/account-statement", statement.getTransactionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[1].status").value("PAID"))
                .andExpect(jsonPath("$.installments[1].paymentSource").value("GATEWAY"))
                .andExpect(jsonPath("$.installments[1].paymentReference").value(providerId))
                .andExpect(jsonPath("$.installments[0].paymentSource").value(nullValue()));
        start(buyer, statement, 2, "attempt-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTALLMENT_CONFLICT"));
    }

    @Test
    void aRedeliveredWebhookOrAnotherEventOfTheSameChargeCreditsNothingAgain() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var statement = statementOf(buyer);
        var body = start(buyer, statement, 3, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String intentId = JsonPath.read(body, "$.id");
        var providerId = providerIdOf(intentId);
        var eventId = "evt_it_" + UUID.randomUUID();
        var payload = succeeded(eventId, providerId, intentId);
        webhook(payload).andExpect(jsonPath("$.outcome").value("CREDITED"));
        var paidAt = installment(statement, 3).getPaidAt();

        webhook(payload).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("CREDITED"));
        webhook(succeeded("evt_it_" + UUID.randomUUID(), providerId, intentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));

        assertEquals(paidAt, installment(statement, 3).getPaidAt());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM financial_document_control.payment_gateway_events "
                + "WHERE event_id = ?", Integer.class, eventId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM financial_document_control.installments "
                + "WHERE payment_reference = ?", Integer.class, providerId));
    }

    @Test
    void aWebhookNotSignedByStripeIsRefusedWithoutAToken() throws Exception {
        var payload = succeeded("evt_it_" + UUID.randomUUID(), "pi_forged", UUID.randomUUID().toString());

        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("Stripe-Signature", sign(payload, "whsec_forged", Instant.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("Stripe-Signature", sign(payload, WEBHOOK_SECRET, Instant.now().minusSeconds(600))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhooksAboutOtherThingsAreAcknowledgedAndIgnored() throws Exception {
        var otherType = paymentIntentEvent("evt_it_" + UUID.randomUUID(), "payment_intent.created", "2026-10-28.endive",
                "pi_other", 500, "requires_payment_method", "{\"app\":\"inmonode\"}", "null");
        var otherApp = paymentIntentEvent("evt_it_" + UUID.randomUUID(), "payment_intent.succeeded",
                "2026-10-28.endive", "pi_mindflow", 500, "succeeded", "{\"app\":\"mindflow\"}", "null");

        webhook(otherType).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("IGNORED"));
        webhook(otherApp).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("IGNORED"));
    }

    @Test
    void aChargeForAnInstallmentTheBackOfficePaidMeanwhileIsUnappliedAndFinanceIsTold() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var statement = statementOf(buyer);
        var body = start(buyer, statement, 4, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String intentId = JsonPath.read(body, "$.id");
        mockMvc.perform(post("/api/v1/account-statements/{id}/installments/{number}/payment", statement.getId(), 4)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 3188.23}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[3].paymentSource").value("BACK_OFFICE"));

        webhook(succeeded("evt_it_" + UUID.randomUUID(), providerIdOf(intentId), intentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNAPPLIED"));

        var installment = installment(statement, 4);
        assertEquals(PaymentSource.BACK_OFFICE, installment.getPaymentSource());
        assertNull(installment.getPaymentReference());
        mockMvc.perform(get(PAYMENT_INTENTS + "/{intentId}", statement.getId(), 4, intentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)))
                .andExpect(jsonPath("$.status").value("UNAPPLIED"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM financial_document_control.notification_outbox "
                + "WHERE event_key = ? AND recipient = '@finance'", Integer.class, "payment-unapplied:" + intentId));
    }

    @Test
    void anotherBuyerCanNeitherPayNorSeeThePayment() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var statement = statementOf(buyer);
        var body = start(buyer, statement, 5, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String intentId = JsonPath.read(body, "$.id");
        var stranger = BUYERS.incrementAndGet();

        start(stranger, statement, 5, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATEMENT_NOT_FOUND"));
        mockMvc.perform(get(PAYMENT_INTENTS + "/{intentId}", statement.getId(), 5, intentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_INTENT_NOT_FOUND"));
        start(buyer, statement, 13, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INSTALLMENT_NOT_FOUND"));
        mockMvc.perform(post(PAYMENT_INTENTS, statement.getId(), 5)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L)))
                .andExpect(status().isForbidden());
        start(buyer, statement, 6, "attempt-1").andExpect(status().isCreated());
        start(buyer, statement, 7, "attempt-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_INTENT_CONFLICT"));
    }

    @Test
    void withoutStripeOnlinePaymentsAreUnavailable() throws Exception {
        when(paymentGateway.isEnabled()).thenReturn(false);
        var buyer = BUYERS.incrementAndGet();
        var statement = statementOf(buyer);

        start(buyer, statement, 1, null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENTS_UNAVAILABLE"));
        webhook(succeeded("evt_it_" + UUID.randomUUID(), "pi_x", UUID.randomUUID().toString()))
                .andExpect(status().isServiceUnavailable());
    }

    private ResultActions start(long buyer, AccountStatement statement, int number, String key) throws Exception {
        var request = post(PAYMENT_INTENTS, statement.getId(), number)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer));
        if (key != null) request.header("Idempotency-Key", key);
        return mockMvc.perform(request);
    }

    private ResultActions webhook(String payload) throws Exception {
        return mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(payload)
                .header("Stripe-Signature", sign(payload, WEBHOOK_SECRET, Instant.now())));
    }

    private static String succeeded(String eventId, String providerId, String intentId) {
        return paymentIntentEvent(eventId, "payment_intent.succeeded", "2026-10-28.endive", providerId, CENTS,
                "succeeded", "{\"app\":\"inmonode\",\"installmentPaymentIntentId\":\"%s\"}".formatted(intentId),
                "null");
    }

    private String providerIdOf(String intentId) {
        return jdbc.queryForObject("SELECT provider_payment_intent_id FROM "
                + "financial_document_control.installment_payment_intents WHERE id = ?", String.class,
                UUID.fromString(intentId));
    }

    private static GatewayPaymentIntent charge(String id, GatewayPaymentStatus status, long amount, long received) {
        return new GatewayPaymentIntent(id, status, amount, received, "pen", "ch_" + id, "secret_" + id, null,
                Map.of("app", "inmonode"));
    }

    private com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment installment(
            AccountStatement statement, int number) {
        return accountStatementRepository.findById(statement.getId()).orElseThrow()
                .findInstallment(number).orElseThrow();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }

    /** 9 000 down on 45 000 in 12 months at 12 %, agreed now: every installment is still pending. */
    private AccountStatement statementOf(long buyerId) {
        var agreedAt = Instant.now();
        var lot = lot();
        var transactionId = UUID.randomUUID();
        var reservation = reservationRepository.save(Reservation.restore(null, lot.getId(), ReservationChannel.WEB,
                buyerId, null, transactionId, Money.of(new BigDecimal("9000")), agreedAt, ReservationStatus.VERIFIED,
                List.of(), agreedAt, new FinancingPlan(Money.of(new BigDecimal("45000")), 12, new BigDecimal("12"))));
        var contract = contractRepository.save(Contract.restore(null, reservation.getId(), transactionId, buyerId,
                lot.getId(), UUID.randomUUID(), "contracts/%s/contract.pdf".formatted(transactionId), 3000,
                ContractStatus.ISSUED, agreedAt, 77L, agreedAt));
        return accountStatementRepository.save(AccountStatement.open(contract, reservation, agreedAt));
    }

    private Lot lot() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, new BigDecimal("1.5"));
        var project = Project.create("Pagos en línea", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "P-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }
}
