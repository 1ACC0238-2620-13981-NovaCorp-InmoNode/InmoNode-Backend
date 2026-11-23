package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.FinancialNotificationOutbox;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.GatewayPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.GatewayPaymentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.InvalidGatewaySignatureException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGateway;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayEvent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayWebhookVerifier;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntentTest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceivePaymentGatewayEventCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.StartInstallmentPaymentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentIntentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentStart;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventRecord;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentSource;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.InstallmentPaymentIntentRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.PaymentGatewayEventRepository;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** US-55 with the gateway, the statement and the lot replaced; the payments and events live in memory. */
class InstallmentPaymentCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
    private static final Long BUYER = 41L;
    private static final long CENTS = 318_823L;

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryIntents intents = new InMemoryIntents();
    private final InMemoryEvents events = new InMemoryEvents();
    private final AccountStatementRepository statements = mock(AccountStatementRepository.class);
    private final LotRepository lots = mock(LotRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentGatewayWebhookVerifier verifier = mock(PaymentGatewayWebhookVerifier.class);
    private final ExternalIamService iam = mock(ExternalIamService.class);
    private final FinancialNotificationOutbox notifications = mock(FinancialNotificationOutbox.class);
    private final AtomicInteger opened = new AtomicInteger();
    private final InstallmentPaymentCommandServiceImpl service = new InstallmentPaymentCommandServiceImpl(statements,
            lots, intents, events, gateway, verifier, iam, notifications, mock(PlatformTransactionManager.class),
            clock, Duration.ofMinutes(30));
    private AccountStatement statement;

    @BeforeEach
    void setUp() {
        statement = InstallmentPaymentIntentTest.statement(null);
        when(statements.findById(1L)).thenAnswer(invocation -> Optional.of(statement));
        when(statements.findByIdForUpdate(1L)).thenAnswer(invocation -> Optional.of(statement));
        when(statements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lots.findByIdForUpdate(3L)).thenReturn(Optional.of(mock(Lot.class)));
        when(iam.currentUserId()).thenReturn(Optional.of(BUYER));
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.publishableKey()).thenReturn("pk_test_1");
        when(verifier.isEnabled()).thenReturn(true);
        when(gateway.open(any())).thenAnswer(invocation -> {
            InstallmentPaymentIntent intent = invocation.getArgument(0);
            return charge("pi_" + opened.incrementAndGet(), GatewayPaymentStatus.PENDING, intent.amountInMinorUnits(), 0);
        });
        when(gateway.retrieve(anyString())).thenAnswer(invocation ->
                charge(invocation.getArgument(0), GatewayPaymentStatus.PENDING, CENTS, 0));
        when(gateway.cancel(anyString())).thenAnswer(invocation ->
                charge(invocation.getArgument(0), GatewayPaymentStatus.CANCELED, CENTS, 0));
    }

    @Test
    void startingOpensAPaymentForWhatTheInstallmentOwesAndSendsItToTheGateway() {
        var start = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-1")));

        assertTrue(start.created());
        assertEquals("secret_pi_1", start.clientSecret());
        assertEquals("pk_test_1", start.publishableKey());
        var intent = intents.findById(start.intent().getId()).orElseThrow();
        assertEquals(InstallmentPaymentIntentStatus.OPEN, intent.getStatus());
        assertEquals("pi_1", intent.getProviderPaymentIntentId());
        assertEquals(new BigDecimal("3188.23"), intent.getAmount());
        assertEquals(NOW.plus(Duration.ofMinutes(30)), intent.getExpiresAt());
        verify(gateway).open(argThat(sent -> sent.getId().equals(intent.getId()) && sent.amountInMinorUnits() == CENTS));
    }

    @Test
    void theSameKeyReturnsTheSamePayment() {
        var first = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-1")));

        var again = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-1")));

        assertFalse(again.created());
        assertEquals(first.intent().getId(), again.intent().getId());
        assertEquals("secret_pi_1", again.clientSecret());
        verify(gateway, times(1)).open(any());
    }

    @Test
    void anotherStartWhileOneIsOpenReturnsThatOne() {
        var first = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-1")));

        var other = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-2")));
        var keyless = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, null)));

        assertEquals(first.intent().getId(), other.intent().getId());
        assertEquals(first.intent().getId(), keyless.intent().getId());
        assertFalse(other.created());
        assertEquals(1, intents.count());
    }

    @Test
    void aKeyUsedForAnotherInstallmentIsAConflict() {
        service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-1"));

        assertEquals("PAYMENT_INTENT_CONFLICT", error(service.handle(new StartInstallmentPaymentCommand(1L, 3, "key-1"))));
    }

    @Test
    void anExpiredPaymentIsCanceledInTheGatewayAndAnotherOneIsOpened() {
        var first = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-1")));
        clock.advance(Duration.ofMinutes(30));

        var second = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-2")));

        assertTrue(second.created());
        assertNotEquals(first.intent().getId(), second.intent().getId());
        assertEquals("secret_pi_2", second.clientSecret());
        verify(gateway).cancel("pi_1");
        assertEquals(InstallmentPaymentIntentStatus.EXPIRED, intents.findById(first.intent().getId()).orElseThrow().getStatus());
        var again = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-1")));
        assertEquals(InstallmentPaymentIntentStatus.EXPIRED, again.intent().getStatus());
        assertNull(again.clientSecret());
    }

    @Test
    void aPaymentOpenedBeforeALateFeeIsReplacedByOneForTheNewAmount() {
        var first = started(service.handle(new StartInstallmentPaymentCommand(1L, 1, null)));
        statement = InstallmentPaymentIntentTest.statement(new BigDecimal("31.99"));

        var second = started(service.handle(new StartInstallmentPaymentCommand(1L, 1, null)));

        assertTrue(second.created());
        assertEquals(new BigDecimal("3220.22"), second.intent().getAmount());
        assertEquals(InstallmentPaymentIntentStatus.CANCELED, intents.findById(first.intent().getId()).orElseThrow().getStatus());
    }

    @Test
    void anExpiredPaymentThatWasChargedIsCreditedInsteadOfOpeningAnother() {
        var first = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, null)));
        clock.advance(Duration.ofMinutes(45));
        when(gateway.retrieve("pi_1")).thenReturn(charge("pi_1", GatewayPaymentStatus.SUCCEEDED, CENTS, CENTS));

        assertEquals("INSTALLMENT_CONFLICT", error(service.handle(new StartInstallmentPaymentCommand(1L, 2, null))));

        var installment = statement.findInstallment(2).orElseThrow();
        assertEquals(InstallmentStatus.PAID, installment.getStatus());
        assertEquals(PaymentSource.GATEWAY, installment.getPaymentSource());
        assertEquals("pi_1", installment.getPaymentReference());
        assertEquals(InstallmentPaymentIntentStatus.SUCCEEDED, intents.findById(first.intent().getId()).orElseThrow().getStatus());
        verify(gateway, never()).cancel(anyString());
        verify(gateway, times(1)).open(any());
    }

    @Test
    void anExpiredPaymentTheGatewayIsProcessingStaysOpen() {
        var first = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, null)));
        clock.advance(Duration.ofMinutes(45));
        when(gateway.retrieve("pi_1")).thenReturn(charge("pi_1", GatewayPaymentStatus.PROCESSING, CENTS, 0));

        var again = started(service.handle(new StartInstallmentPaymentCommand(1L, 2, null)));

        assertEquals(first.intent().getId(), again.intent().getId());
        assertEquals(InstallmentPaymentIntentStatus.OPEN, again.intent().getStatus());
        assertNull(again.clientSecret());
        verify(gateway, never()).cancel(anyString());
    }

    @Test
    void aPaidInstallmentOrAnotherBuyersStatementCannotBePaid() {
        statement.registerInstallmentPayment(2, new BigDecimal("3188.23"), NOW);
        assertEquals("INSTALLMENT_CONFLICT", error(service.handle(new StartInstallmentPaymentCommand(1L, 2, null))));
        assertEquals("INSTALLMENT_NOT_FOUND", error(service.handle(new StartInstallmentPaymentCommand(1L, 13, null))));

        when(iam.currentUserId()).thenReturn(Optional.of(42L));
        assertEquals("ACCOUNT_STATEMENT_NOT_FOUND", error(service.handle(new StartInstallmentPaymentCommand(1L, 3, null))));
        verify(gateway, never()).open(any());
    }

    @Test
    void whenTheGatewayFailsNothingIsLeftOpen() {
        doThrow(new PaymentGatewayException("Stripe is down")).when(gateway).open(any());

        assertEquals("PAYMENT_GATEWAY_ERROR", error(service.handle(new StartInstallmentPaymentCommand(1L, 2, "key-1"))));

        assertEquals(1, intents.count());
        assertTrue(intents.findOpen(1L, 2).isEmpty());
        var canceled = intents.findByIdempotencyKey(BUYER, "key-1").orElseThrow();
        assertEquals(InstallmentPaymentIntentStatus.CANCELED, canceled.getStatus());
    }

    @Test
    void withoutAGatewayPaymentsAreUnavailable() {
        when(gateway.isEnabled()).thenReturn(false);
        assertEquals("PAYMENTS_UNAVAILABLE", error(service.handle(new StartInstallmentPaymentCommand(1L, 2, null))));
        assertEquals("PAYMENTS_UNAVAILABLE", error(service.handle(new ReceivePaymentGatewayEventCommand("{}", "t=1"))));

        when(gateway.isEnabled()).thenReturn(true);
        when(verifier.isEnabled()).thenReturn(false);
        assertEquals("PAYMENTS_UNAVAILABLE", error(service.handle(new StartInstallmentPaymentCommand(1L, 2, null))));
        assertEquals(0, intents.count());
    }

    @Test
    void aConfirmedChargeCreditsTheInstallmentOnce() {
        var intent = startedIntent(2);
        succeeded("pi_1", CENTS);

        assertEquals(PaymentGatewayEventOutcome.CREDITED, received("evt_1", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED).outcome());

        var installment = statement.findInstallment(2).orElseThrow();
        assertEquals(InstallmentStatus.PAID, installment.getStatus());
        assertEquals(PaymentSource.GATEWAY, installment.getPaymentSource());
        assertEquals("pi_1", installment.getPaymentReference());
        assertEquals(NOW, installment.getPaidAt());
        var confirmed = intents.findById(intent.getId()).orElseThrow();
        assertEquals(InstallmentPaymentIntentStatus.SUCCEEDED, confirmed.getStatus());
        assertEquals("ch_pi_1", confirmed.getProviderChargeId());
        assertEquals(PaymentGatewayEventOutcome.CREDITED, events.findById("evt_1").orElseThrow().outcome());
        verify(statements, times(1)).save(statement);
    }

    @Test
    void aRedeliveredNotificationGetsItsFirstOutcomeAndCreditsNothing() {
        startedIntent(2);
        succeeded("pi_1", CENTS);
        received("evt_1", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED);

        assertEquals(PaymentGatewayEventOutcome.CREDITED, received("evt_1", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED).outcome());
        assertEquals(PaymentGatewayEventOutcome.DUPLICATE, received("evt_2", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED).outcome());

        verify(statements, times(1)).save(statement);
        verify(gateway, times(2)).retrieve("pi_1");
    }

    @Test
    void notificationsOfOtherAppsOrOtherEnvironmentsAreIgnored() {
        startedIntent(2);
        when(verifier.verify(anyString(), anyString())).thenReturn(new PaymentGatewayEvent("evt_mindflow",
                "payment_intent.succeeded", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED, "pi_1", "mindflow", null, null));
        assertEquals(PaymentGatewayEventOutcome.IGNORED, recorded(service.handle(command())).outcome());

        when(verifier.verify(anyString(), anyString())).thenReturn(new PaymentGatewayEvent("evt_teammate",
                "payment_intent.succeeded", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED, "pi_elsewhere", "inmonode",
                UUID.randomUUID().toString(), null));
        assertEquals(PaymentGatewayEventOutcome.IGNORED, recorded(service.handle(command())).outcome());

        when(verifier.verify(anyString(), anyString())).thenReturn(new PaymentGatewayEvent("evt_charge",
                "charge.succeeded", PaymentGatewayEvent.Kind.OTHER, null, null, null, null));
        assertEquals(PaymentGatewayEventOutcome.IGNORED, recorded(service.handle(command())).outcome());

        assertEquals(InstallmentStatus.PENDING, statement.findInstallment(2).orElseThrow().getStatus());
        verify(gateway, never()).retrieve(anyString());
    }

    @Test
    void aChargeTheGatewayDoesNotShowAsNotifiedIsRejected() {
        var intent = startedIntent(2);
        succeeded("pi_1", CENTS - 100);
        assertEquals(PaymentGatewayEventOutcome.REJECTED, received("evt_1", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED).outcome());

        when(gateway.retrieve("pi_1")).thenReturn(charge("pi_1", GatewayPaymentStatus.PENDING, CENTS, 0));
        assertEquals(PaymentGatewayEventOutcome.REJECTED, received("evt_2", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED).outcome());

        assertEquals(InstallmentStatus.PENDING, statement.findInstallment(2).orElseThrow().getStatus());
        assertTrue(intents.findById(intent.getId()).orElseThrow().isOpen());
    }

    @Test
    void aChargeForAnInstallmentThatWasPaidMeanwhileIsUnappliedAndFinanceIsTold() {
        var intent = startedIntent(2);
        statement.registerInstallmentPayment(2, new BigDecimal("3188.23"), NOW);
        succeeded("pi_1", CENTS);

        var record = received("evt_1", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED);

        assertEquals(PaymentGatewayEventOutcome.UNAPPLIED, record.outcome());
        assertTrue(record.detail().contains("already paid"));
        assertEquals(PaymentSource.BACK_OFFICE, statement.findInstallment(2).orElseThrow().getPaymentSource());
        var unapplied = intents.findById(intent.getId()).orElseThrow();
        assertEquals(InstallmentPaymentIntentStatus.UNAPPLIED, unapplied.getStatus());
        verify(notifications).enqueue(eq("payment-unapplied:" + intent.getId()), eq("@finance"),
                eq("Pago en línea sin aplicar"), contains("pi_1"));
    }

    @Test
    void aDeclineKeepsThePaymentOpenWithItsReason() {
        var intent = startedIntent(2);
        when(verifier.verify(anyString(), anyString())).thenReturn(new PaymentGatewayEvent("evt_1",
                "payment_intent.payment_failed", PaymentGatewayEvent.Kind.PAYMENT_FAILED, "pi_1", "inmonode",
                intent.getId().toString(), "Your card was declined."));

        assertEquals(PaymentGatewayEventOutcome.FAILED, recorded(service.handle(command())).outcome());

        var declined = intents.findById(intent.getId()).orElseThrow();
        assertTrue(declined.isOpen());
        assertEquals("Your card was declined.", declined.getLastFailure());
    }

    @Test
    void aChargeCanceledInTheGatewayClosesThePayment() {
        var intent = startedIntent(2);

        assertEquals(PaymentGatewayEventOutcome.CANCELED, received("evt_1", PaymentGatewayEvent.Kind.PAYMENT_CANCELED).outcome());

        assertEquals(InstallmentPaymentIntentStatus.CANCELED, intents.findById(intent.getId()).orElseThrow().getStatus());
    }

    @Test
    void aNotificationNotSignedByTheGatewayIsRefused() {
        when(verifier.verify(anyString(), any())).thenThrow(new InvalidGatewaySignatureException("bad signature"));

        assertEquals("INVALID_WEBHOOK_SIGNATURE", error(service.handle(new ReceivePaymentGatewayEventCommand("{}", null))));
        assertEquals(0, events.count());
    }

    @Test
    void whenTheGatewayCannotConfirmTheChargeTheNotificationIsNotRecorded() {
        startedIntent(2);
        doThrow(new PaymentGatewayException("Stripe is down")).when(gateway).retrieve("pi_1");

        stubEvent("evt_1", PaymentGatewayEvent.Kind.PAYMENT_SUCCEEDED, "pi_1");
        assertEquals("PAYMENT_GATEWAY_ERROR", error(service.handle(command())));

        assertEquals(0, events.count());
        assertEquals(InstallmentStatus.PENDING, statement.findInstallment(2).orElseThrow().getStatus());
    }

    private InstallmentPaymentIntent startedIntent(int number) {
        return started(service.handle(new StartInstallmentPaymentCommand(1L, number, null))).intent();
    }

    private void succeeded(String providerId, long received) {
        when(gateway.retrieve(providerId)).thenReturn(charge(providerId, GatewayPaymentStatus.SUCCEEDED, CENTS, received));
    }

    private PaymentGatewayEventRecord received(String eventId, PaymentGatewayEvent.Kind kind) {
        stubEvent(eventId, kind, "pi_1");
        return recorded(service.handle(command()));
    }

    private void stubEvent(String eventId, PaymentGatewayEvent.Kind kind, String providerId) {
        when(verifier.verify(anyString(), anyString())).thenReturn(new PaymentGatewayEvent(eventId, "payment_intent.x",
                kind, providerId, "inmonode", null, null));
    }

    private static ReceivePaymentGatewayEventCommand command() {
        return new ReceivePaymentGatewayEventCommand("{\"id\":\"evt\"}", "t=1,v1=abc");
    }

    private static GatewayPaymentIntent charge(String id, GatewayPaymentStatus status, long amount, long received) {
        return new GatewayPaymentIntent(id, status, amount, received, "pen", "ch_" + id, "secret_" + id, null,
                Map.of("app", "inmonode"));
    }

    private static InstallmentPaymentStart started(Result<InstallmentPaymentStart, ApplicationError> result) {
        return switch (result) {
            case Result.Success<InstallmentPaymentStart, ApplicationError> success -> success.value();
            case Result.Failure<InstallmentPaymentStart, ApplicationError> failure ->
                    fail("expected a payment, got " + failure.error());
        };
    }

    private static PaymentGatewayEventRecord recorded(Result<PaymentGatewayEventRecord, ApplicationError> result) {
        return switch (result) {
            case Result.Success<PaymentGatewayEventRecord, ApplicationError> success -> success.value();
            case Result.Failure<PaymentGatewayEventRecord, ApplicationError> failure ->
                    fail("expected an outcome, got " + failure.error());
        };
    }

    private static String error(Result<?, ApplicationError> result) {
        if (result instanceof Result.Failure<?, ApplicationError> failure) return failure.error().code();
        return fail("expected a failure, got " + result);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) { this.now = now; }

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Saves copies, as the database does, so a change is seen only once it is saved. */
    private static final class InMemoryIntents implements InstallmentPaymentIntentRepository {
        private final Map<UUID, InstallmentPaymentIntent> rows = new LinkedHashMap<>();

        int count() { return rows.size(); }

        @Override
        public void save(InstallmentPaymentIntent intent) {
            var open = rows.values().stream().filter(InstallmentPaymentIntent::isOpen)
                    .filter(row -> !row.getId().equals(intent.getId()))
                    .anyMatch(row -> row.isFor(intent.getAccountStatementId(), intent.getInstallmentNumber()));
            if (intent.isOpen() && open) throw new IllegalStateException("two open payments of one installment");
            rows.put(intent.getId(), copy(intent));
        }

        @Override public Optional<InstallmentPaymentIntent> findById(UUID id) {
            return Optional.ofNullable(rows.get(id)).map(InMemoryIntents::copy);
        }
        @Override public Optional<InstallmentPaymentIntent> findByIdForUpdate(UUID id) { return findById(id); }
        @Override public Optional<InstallmentPaymentIntent> findOpen(Long statementId, int number) {
            return rows.values().stream().filter(InstallmentPaymentIntent::isOpen)
                    .filter(row -> row.isFor(statementId, number)).findFirst().map(InMemoryIntents::copy);
        }
        @Override public Optional<InstallmentPaymentIntent> findOpenForUpdate(Long statementId, int number) {
            return findOpen(statementId, number);
        }
        @Override public Optional<InstallmentPaymentIntent> findByIdempotencyKey(Long buyerId, String key) {
            return rows.values().stream().filter(row -> row.belongsTo(buyerId) && key.equals(row.getIdempotencyKey()))
                    .findFirst().map(InMemoryIntents::copy);
        }
        @Override public Optional<InstallmentPaymentIntent> findByProviderPaymentIntentId(String providerId) {
            return rows.values().stream().filter(row -> providerId.equals(row.getProviderPaymentIntentId()))
                    .findFirst().map(InMemoryIntents::copy);
        }

        private static InstallmentPaymentIntent copy(InstallmentPaymentIntent intent) {
            return InstallmentPaymentIntent.restore(intent.getId(), intent.getAccountStatementId(),
                    intent.getInstallmentNumber(), intent.getBuyerId(), intent.getAmount(), intent.getCurrency(),
                    intent.getIdempotencyKey(), intent.getCreatedAt(), intent.getExpiresAt(), intent.getStatus(),
                    intent.getProviderPaymentIntentId(), intent.getProviderChargeId(), intent.getLastFailure(),
                    intent.getConfirmedAt());
        }
    }

    private static final class InMemoryEvents implements PaymentGatewayEventRepository {
        private final Map<String, PaymentGatewayEventRecord> rows = new LinkedHashMap<>();

        int count() { return rows.size(); }

        @Override public Optional<PaymentGatewayEventRecord> findById(String eventId) {
            return Optional.ofNullable(rows.get(eventId));
        }

        @Override public boolean record(PaymentGatewayEventRecord event) {
            return rows.putIfAbsent(event.eventId(), event) == null;
        }
    }
}
