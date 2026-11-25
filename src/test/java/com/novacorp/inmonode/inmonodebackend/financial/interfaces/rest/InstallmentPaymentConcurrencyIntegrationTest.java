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
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingPlan;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventRecord;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentSource;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.PaymentGatewayEventRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe.StripeSignatures.paymentIntentEvent;
import static com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe.StripeSignatures.sign;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T91: Stripe's webhook delivered several times at once, and racing the back office, against a real PostgreSQL. The
 * winner may be any of them, so the tests check what must hold whatever the order: the money is credited once, and
 * the outcome kept for an event is the one of the delivery that applied it. Only the calls to Stripe are replaced;
 * the webhooks are signed with the test-only secret of the test profile and verified for real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class InstallmentPaymentConcurrencyIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_test-only-secret";
    private static final String PAYMENT_INTENTS = "/api/v1/account-statements/{id}/installments/{number}/payment-intents";
    private static final String WEBHOOK = "/api/v1/webhooks/stripe";
    private static final long CENTS = 318_823L;
    private static final AtomicLong BUYERS = new AtomicLong(43_000);
    private static final AtomicInteger CHARGES = new AtomicInteger();

    @MockitoBean
    private PaymentGateway paymentGateway;

    @MockitoSpyBean
    private PaymentGatewayEventRepository eventRepository;

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
            return charge("pi_cc_" + CHARGES.incrementAndGet(), GatewayPaymentStatus.PENDING,
                    intent.amountInMinorUnits(), 0);
        });
        when(paymentGateway.retrieve(anyString())).thenAnswer(invocation ->
                charge(invocation.getArgument(0), GatewayPaymentStatus.SUCCEEDED, CENTS, CENTS));
    }

    @RepeatedTest(3)
    void theSameEventDeliveredFourTimesAtOnceCreditsTheInstallmentOnce() throws Exception {
        var statement = statementOf(BUYERS.incrementAndGet());
        var intentId = startPayment(statement, 2);
        var eventId = "evt_cc_" + UUID.randomUUID();
        var payload = succeeded(eventId, providerIdOf(intentId), intentId);

        var answers = atOnce(List.of(() -> webhook(payload), () -> webhook(payload), () -> webhook(payload),
                () -> webhook(payload)));

        assertAll(answers.stream().map(answer -> () -> assertEquals(200, answer.getStatus())));
        assertEquals(List.of("CREDITED", "CREDITED", "CREDITED", "CREDITED"), outcomes(answers),
                "every delivery answers the outcome of the one that credited it");
        var paid = installment(statement, 2);
        assertEquals(InstallmentStatus.PAID, paid.getStatus());
        assertEquals(PaymentSource.GATEWAY, paid.getPaymentSource());
        assertEquals(new BigDecimal("3188.23"), paid.getPaidAmount());
        assertEquals(1, countOf("installments WHERE payment_reference = ?", providerIdOf(intentId)));
        assertEquals("CREDITED", outcomeOf(eventId));
    }

    /**
     * Two deliveries of one event: the first credits the installment and is slow to log it, as a pause of the JVM
     * after its commit would make it. The second one, which waited for the lock, finds the payment confirmed. The
     * log must still keep the outcome of the delivery that credited it, not the duplicate's.
     */
    @Test
    void anEventKeepsTheOutcomeOfTheDeliveryThatCreditedItEvenIfItIsSlowToLogIt() throws Exception {
        var statement = statementOf(BUYERS.incrementAndGet());
        var intentId = startPayment(statement, 3);
        var eventId = "evt_cc_" + UUID.randomUUID();
        var payload = succeeded(eventId, providerIdOf(intentId), intentId);
        doAnswer(invocation -> {
            Thread.sleep(800);
            return invocation.callRealMethod();
        }).when(eventRepository).record(argThat((PaymentGatewayEventRecord event) ->
                event.outcome() == PaymentGatewayEventOutcome.CREDITED));

        var answers = atOnce(List.of(() -> webhook(payload), () -> {
            Thread.sleep(150);
            return webhook(payload);
        }));

        assertEquals(List.of("CREDITED", "CREDITED"), outcomes(answers));
        assertEquals("CREDITED", outcomeOf(eventId));
        assertEquals(PaymentSource.GATEWAY, installment(statement, 3).getPaymentSource());
    }

    @RepeatedTest(3)
    void theWebhookAndTheBackOfficePayingTheSameInstallmentAtOncePayItOnce() throws Exception {
        var statement = statementOf(BUYERS.incrementAndGet());
        var intentId = startPayment(statement, 4);
        var eventId = "evt_cc_" + UUID.randomUUID();
        var payload = succeeded(eventId, providerIdOf(intentId), intentId);

        var answers = atOnce(List.of(() -> webhook(payload), () -> payAtTheOffice(statement, 4)));

        var webhook = answers.get(0);
        var office = answers.get(1);
        assertEquals(200, webhook.getStatus());
        var paid = installment(statement, 4);
        assertEquals(InstallmentStatus.PAID, paid.getStatus());
        assertEquals(new BigDecimal("3188.23"), paid.getPaidAmount());
        var unapplied = countOf("notification_outbox WHERE event_key = ?", "payment-unapplied:" + intentId);
        if (paid.getPaymentSource() == PaymentSource.GATEWAY) {
            assertEquals(409, office.getStatus(), "the office finds it paid online");
            assertEquals("INSTALLMENT_CONFLICT", JsonPath.read(office.getContentAsString(), "$.code"));
            assertEquals("CREDITED", JsonPath.read(webhook.getContentAsString(), "$.outcome"));
            assertEquals(0, unapplied);
        } else {
            assertEquals(PaymentSource.BACK_OFFICE, paid.getPaymentSource());
            assertEquals(200, office.getStatus());
            assertEquals("UNAPPLIED", JsonPath.read(webhook.getContentAsString(), "$.outcome"),
                    "the charge found it paid at the office");
            assertEquals(1, unapplied, "finance is told to refund it");
            assertNull(paid.getPaymentReference());
        }
        assertEquals(JsonPath.read(webhook.getContentAsString(), "$.outcome"), outcomeOf(eventId));
    }

    // ---------------------------------------------------------------- helpers

    /** Runs the calls at the same moment, each in its own thread, and answers their responses in order. */
    private static List<MockHttpServletResponse> atOnce(List<Callable<MockHttpServletResponse>> calls) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.size())) {
            var futures = calls.stream().map(call -> executor.submit(() -> {
                start.await();
                return call.call();
            })).toList();
            start.countDown();
            var answers = new ArrayList<MockHttpServletResponse>();
            for (var future : futures) {
                answers.add(future.get(60, TimeUnit.SECONDS));
            }
            return answers;
        }
    }

    private static List<String> outcomes(List<MockHttpServletResponse> answers) throws Exception {
        var outcomes = new ArrayList<String>();
        for (var answer : answers) {
            outcomes.add(JsonPath.read(answer.getContentAsString(), "$.outcome"));
        }
        return outcomes;
    }

    private String startPayment(AccountStatement statement, int number) throws Exception {
        var body = mockMvc.perform(post(PAYMENT_INTENTS, statement.getId(), number)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, statement.getBuyerId())))
                .andReturn().getResponse();
        assertEquals(201, body.getStatus(), body.getContentAsString());
        return JsonPath.read(body.getContentAsString(), "$.id");
    }

    private MockHttpServletResponse webhook(String payload) throws Exception {
        return mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(payload)
                .header("Stripe-Signature", sign(payload, WEBHOOK_SECRET, Instant.now()))).andReturn().getResponse();
    }

    private MockHttpServletResponse payAtTheOffice(AccountStatement statement, int number) throws Exception {
        return mockMvc.perform(post("/api/v1/account-statements/{id}/installments/{number}/payment",
                        statement.getId(), number)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 3188.23}")).andReturn().getResponse();
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

    private String outcomeOf(String eventId) {
        return jdbc.queryForObject("SELECT outcome FROM financial_document_control.payment_gateway_events "
                + "WHERE event_id = ?", String.class, eventId);
    }

    private int countOf(String fromWhere, Object argument) {
        return jdbc.queryForObject("SELECT count(*) FROM financial_document_control." + fromWhere, Integer.class,
                argument);
    }

    private static GatewayPaymentIntent charge(String id, GatewayPaymentStatus status, long amount, long received) {
        return new GatewayPaymentIntent(id, status, amount, received, "pen", "ch_" + id, "secret_" + id, null,
                Map.of("app", "inmonode"));
    }

    private Installment installment(AccountStatement statement, int number) {
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
        var project = Project.create("Pagos simultáneos", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "C-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }
}
