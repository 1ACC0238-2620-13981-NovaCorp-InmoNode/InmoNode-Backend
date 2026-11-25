package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.FinancialNotificationOutbox;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.GatewayPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.InvalidGatewaySignatureException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGateway;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayEvent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayWebhookVerifier;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceivePaymentGatewayEventCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.StartInstallmentPaymentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentStart;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventRecord;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.InstallmentPaymentIntentRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.PaymentGatewayEventRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.InstallmentPaymentCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * US-55. The gateway is never called inside a transaction, so no lock is held while waiting for it: starting a
 * payment records it under the locks (lot, then statement, then payment, as every change of a statement), calls the
 * gateway, and keeps the gateway's id in a second transaction. Crediting takes the same locks, so a payment of the
 * back office and one of the gateway are made one after the other and only one of them pays the installment.
 */
@Service
public class InstallmentPaymentCommandServiceImpl implements InstallmentPaymentCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(InstallmentPaymentCommandServiceImpl.class);

    private final AccountStatementRepository accountStatementRepository;
    private final LotRepository lotRepository;
    private final InstallmentPaymentIntentRepository intentRepository;
    private final PaymentGatewayEventRepository eventRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentGatewayWebhookVerifier webhookVerifier;
    private final ExternalIamService externalIamService;
    private final FinancialNotificationOutbox notifications;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration timeToLive;

    public InstallmentPaymentCommandServiceImpl(AccountStatementRepository accountStatementRepository,
                                                LotRepository lotRepository,
                                                InstallmentPaymentIntentRepository intentRepository,
                                                PaymentGatewayEventRepository eventRepository,
                                                PaymentGateway paymentGateway,
                                                PaymentGatewayWebhookVerifier webhookVerifier,
                                                ExternalIamService externalIamService,
                                                FinancialNotificationOutbox notifications,
                                                PlatformTransactionManager transactionManager, Clock clock,
                                                @Value("${financial.payments.intent-ttl:PT30M}") Duration timeToLive) {
        this.accountStatementRepository = accountStatementRepository;
        this.lotRepository = lotRepository;
        this.intentRepository = intentRepository;
        this.eventRepository = eventRepository;
        this.paymentGateway = paymentGateway;
        this.webhookVerifier = webhookVerifier;
        this.externalIamService = externalIamService;
        this.notifications = notifications;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.timeToLive = timeToLive;
    }

    @Override
    public Result<InstallmentPaymentStart, ApplicationError> handle(StartInstallmentPaymentCommand command) {
        if (!isAvailable()) {
            return Result.failure(unavailable());
        }
        var buyerId = externalIamService.currentUserId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        try {
            return start(command, buyerId);
        } catch (PaymentGatewayException ex) {
            LOG.warn("The payment gateway failed while starting the payment of installment {} of account statement {}: {}",
                    command.installmentNumber(), command.accountStatementId(), ex.getMessage());
            return Result.failure(gatewayError());
        } catch (DuplicateKeyException ex) {
            // Two starts at once with one key for different installments: the other one took it.
            return Result.failure(keyUsedElsewhere());
        }
    }

    private Result<InstallmentPaymentStart, ApplicationError> start(StartInstallmentPaymentCommand command,
                                                                    Long buyerId) {
        var statementId = command.accountStatementId();
        var number = command.installmentNumber();
        var key = command.idempotencyKey();
        if (key != null) {
            var same = intentRepository.findByIdempotencyKey(buyerId, key).orElse(null);
            if (same != null) {
                if (!same.isFor(statementId, number)) {
                    return Result.failure(keyUsedElsewhere());
                }
                return Result.success(present(settleIfStale(same), false));
            }
        }
        var statement = accountStatementRepository.findById(statementId)
                .filter(found -> found.belongsTo(buyerId))
                .orElse(null);
        if (statement == null) {
            return Result.failure(ApplicationError.notFound("account_statement", String.valueOf(statementId)));
        }
        var installment = statement.findInstallment(number).orElse(null);
        if (installment == null) {
            return Result.failure(ApplicationError.notFound("installment",
                    "%d of account statement %d".formatted(number, statementId)));
        }
        if (installment.isPaid()) {
            return Result.failure(alreadyPaid(installment));
        }
        var open = intentRepository.findOpen(statementId, number).orElse(null);
        if (open != null) {
            var settled = settleIfStale(open);
            if (settled.isOpen()) {
                return Result.success(present(settled, false));
            }
        }
        var opened = Objects.requireNonNull(transactionTemplate.execute(status ->
                openUnderLock(statementId, number, buyerId, key)));
        return switch (opened) {
            case Result.Failure<Opened, ApplicationError> failure -> Result.failure(failure.error());
            case Result.Success<Opened, ApplicationError> success when !success.value().created() ->
                    Result.success(present(success.value().intent(), false));
            case Result.Success<Opened, ApplicationError> success ->
                    Result.success(openInGateway(success.value().intent()));
        };
    }

    private record Opened(InstallmentPaymentIntent intent, boolean created) {}

    private Result<Opened, ApplicationError> openUnderLock(Long statementId, int number, Long buyerId,
                                                           @Nullable String key) {
        var statement = lockStatement(statementId);
        var installment = statement.findInstallment(number).orElseThrow();
        if (installment.isPaid()) {
            return Result.failure(alreadyPaid(installment));
        }
        var now = now();
        var open = intentRepository.findOpenForUpdate(statementId, number).orElse(null);
        if (open != null) {
            if (!isStale(open, installment, now)) {
                return Result.success(new Opened(open, false));
            }
            close(open, now);
            intentRepository.save(open);
        }
        var intent = InstallmentPaymentIntent.open(UUID.randomUUID(), statement, installment, buyerId, key, now,
                timeToLive);
        intentRepository.save(intent);
        return Result.success(new Opened(intent, true));
    }

    private InstallmentPaymentStart openInGateway(InstallmentPaymentIntent intent) {
        GatewayPaymentIntent charge;
        try {
            charge = paymentGateway.open(intent);
        } catch (PaymentGatewayException ex) {
            // It was never opened there, so it can never be paid: it is closed instead of being left open.
            transactionTemplate.executeWithoutResult(status -> intentRepository.findByIdForUpdate(intent.getId())
                    .filter(InstallmentPaymentIntent::isOpen)
                    .ifPresent(open -> {
                        open.cancel("the gateway could not open it: " + ex.getMessage());
                        intentRepository.save(open);
                    }));
            throw ex;
        }
        return new InstallmentPaymentStart(attach(intent.getId(), charge.id()), charge.clientSecret(),
                paymentGateway.publishableKey(), true);
    }

    private InstallmentPaymentIntent attach(UUID intentId, String providerId) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            var intent = intentRepository.findByIdForUpdate(intentId).orElseThrow();
            if (intent.isOpen()) {
                intent.attach(providerId);
                intentRepository.save(intent);
            }
            return intent;
        }));
    }

    /** A payment as the app needs it: with the secret to pay it while it can still be paid. */
    private InstallmentPaymentStart present(InstallmentPaymentIntent intent, boolean created) {
        if (!intent.isOpen() || intent.isExpired(now())) {
            return new InstallmentPaymentStart(intent, null, paymentGateway.publishableKey(), created);
        }
        var providerId = intent.getProviderPaymentIntentId();
        if (providerId == null) {
            // Recorded while the gateway was being called, or the call was cut: the gateway answers the same charge.
            var charge = paymentGateway.open(intent);
            return new InstallmentPaymentStart(attach(intent.getId(), charge.id()), charge.clientSecret(),
                    paymentGateway.publishableKey(), created);
        }
        return new InstallmentPaymentStart(intent, paymentGateway.retrieve(providerId).clientSecret(),
                paymentGateway.publishableKey(), created);
    }

    /**
     * An open payment that expired, or no longer matches what its installment owes, is settled with the gateway
     * before another one is opened, since its notification may come late after the buyer paid it: it is credited
     * when it was paid, stays open while the gateway processes it, and is canceled there and closed here otherwise.
     *
     * @return the payment as it ends up
     */
    private InstallmentPaymentIntent settleIfStale(InstallmentPaymentIntent intent) {
        if (!intent.isOpen()) {
            return intent;
        }
        var installment = accountStatementRepository.findById(intent.getAccountStatementId())
                .flatMap(statement -> statement.findInstallment(intent.getInstallmentNumber()))
                .orElseThrow();
        if (!isStale(intent, installment, now())) {
            return intent;
        }
        var providerId = intent.getProviderPaymentIntentId();
        if (providerId != null) {
            var charge = paymentGateway.retrieve(providerId);
            switch (charge.status()) {
                case SUCCEEDED -> {
                    var applied = Objects.requireNonNull(transactionTemplate.execute(status ->
                            applyCharge(intent.getId(), charge)));
                    LOG.info("Payment {} was charged before its notification came: {}", intent.getId(),
                            applied.outcome());
                    return intentRepository.findById(intent.getId()).orElseThrow();
                }
                case PROCESSING -> {
                    return intent;
                }
                case PENDING -> paymentGateway.cancel(providerId);
                case CANCELED -> { }
            }
        }
        transactionTemplate.executeWithoutResult(status -> {
            var statement = lockStatement(intent.getAccountStatementId());
            var locked = intentRepository.findByIdForUpdate(intent.getId()).orElseThrow();
            var now = now();
            if (locked.isOpen() && isStale(locked, statement.findInstallment(locked.getInstallmentNumber())
                    .orElseThrow(), now)) {
                close(locked, now);
                intentRepository.save(locked);
            }
        });
        return intentRepository.findById(intent.getId()).orElseThrow();
    }

    private static boolean isStale(InstallmentPaymentIntent intent, Installment installment, Instant now) {
        return intent.isExpired(now) || installment.isPaid() || !installment.isSettledBy(intent.getAmount());
    }

    private static void close(InstallmentPaymentIntent intent, Instant now) {
        if (intent.isExpired(now)) {
            intent.expire();
        } else {
            intent.cancel("the installment was paid, or what it owes changed");
        }
    }

    @Override
    public Result<PaymentGatewayEventRecord, ApplicationError> handle(ReceivePaymentGatewayEventCommand command) {
        if (!isAvailable()) {
            return Result.failure(unavailable());
        }
        PaymentGatewayEvent event;
        try {
            event = webhookVerifier.verify(command.payload(), command.signature());
        } catch (InvalidGatewaySignatureException ex) {
            LOG.warn("Rejected a notification that is not signed by the payment gateway: {}", ex.getMessage());
            return Result.failure(new ApplicationError("INVALID_WEBHOOK_SIGNATURE",
                    "The notification is not signed by the payment gateway", ex.getMessage()));
        }
        var known = eventRepository.findById(event.id());
        if (known.isPresent()) {
            return Result.success(known.get());
        }
        Logged logged;
        try {
            logged = process(event);
        } catch (PaymentGatewayException ex) {
            LOG.warn("Could not check event {} with the payment gateway; it will be sent again: {}", event.id(),
                    ex.getMessage());
            return Result.failure(gatewayError());
        }
        var record = logged.answer();
        if (!logged.first()) {
            return Result.success(record);
        }
        if (record.outcome() == PaymentGatewayEventOutcome.REJECTED
                || record.outcome() == PaymentGatewayEventOutcome.UNAPPLIED) {
            LOG.warn("Payment gateway event {} ({}) of {}: {}, {}", event.id(), event.type(), event.providerId(),
                    record.outcome(), record.detail());
        } else {
            LOG.info("Payment gateway event {} ({}) of {}: {}", event.id(), event.type(), event.providerId(),
                    record.outcome());
        }
        return Result.success(record);
    }

    /** What an event answers, and whether this delivery is the one that logged it. */
    private record Logged(PaymentGatewayEventRecord answer, boolean first) {}

    /**
     * Logs what an event did; inside the transaction that did it when there is one, so the outcome kept for an
     * event is always the one of the delivery that applied it, even when two deliveries of it run at once. A
     * delivery that finds it logged answers what was kept.
     */
    private Logged record(PaymentGatewayEvent event, PaymentGatewayEventOutcome outcome, @Nullable String detail) {
        var record = new PaymentGatewayEventRecord(event.id(), event.type(), event.providerId(), outcome, detail, now());
        if (eventRepository.record(record)) {
            return new Logged(record, true);
        }
        return new Logged(eventRepository.findById(event.id()).orElse(record), false);
    }

    private Logged process(PaymentGatewayEvent event) {
        if (event.kind() == PaymentGatewayEvent.Kind.OTHER) {
            return record(event, PaymentGatewayEventOutcome.IGNORED, "not about the charge of an installment");
        }
        if (!event.isOfThisApp()) {
            return record(event, PaymentGatewayEventOutcome.IGNORED, "the charge is of another app");
        }
        var intent = intentOf(event).orElse(null);
        if (intent == null) {
            return record(event, PaymentGatewayEventOutcome.IGNORED, "no payment of this environment has that charge");
        }
        return switch (event.kind()) {
            case PAYMENT_SUCCEEDED -> confirmCharge(event, intent, Objects.requireNonNull(event.providerId()));
            case PAYMENT_FAILED -> recordFailure(event, intent.getId(), event.failureMessage());
            case PAYMENT_CANCELED -> recordCancellation(event, intent.getId());
            case OTHER -> throw new IllegalStateException("unreachable");
        };
    }

    private Optional<InstallmentPaymentIntent> intentOf(PaymentGatewayEvent event) {
        var providerId = event.providerId();
        if (providerId == null) {
            return Optional.empty();
        }
        var byProvider = intentRepository.findByProviderPaymentIntentId(providerId);
        if (byProvider.isPresent()) {
            return byProvider;
        }
        // It may come before the gateway's id was kept here; the metadata of the charge names the payment.
        var reference = uuid(event.intentReference());
        return reference == null ? Optional.empty()
                : intentRepository.findById(reference).filter(found -> found.getProviderPaymentIntentId() == null);
    }

    /** Checked with the gateway as the charge is now, not only as the notification tells it. */
    private Logged confirmCharge(PaymentGatewayEvent event, InstallmentPaymentIntent intent, String providerId) {
        var charge = paymentGateway.retrieve(providerId);
        if (!charge.isSucceeded()) {
            return record(event, PaymentGatewayEventOutcome.REJECTED,
                    "the gateway shows the charge as " + charge.status());
        }
        if (charge.amountReceived() != intent.amountInMinorUnits() || !charge.currency().equals(intent.getCurrency())) {
            return record(event, PaymentGatewayEventOutcome.REJECTED,
                    "the gateway charged %d %s in minor units, and payment %s is of %d %s".formatted(
                            charge.amountReceived(), charge.currency(), intent.getId(), intent.amountInMinorUnits(),
                            intent.getCurrency()));
        }
        return confirm(event, intent.getId(), charge);
    }

    /** The webhook's confirmation, logged in the transaction that credits it. */
    private Logged confirm(PaymentGatewayEvent event, UUID intentId, GatewayPaymentIntent charge) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            var applied = applyCharge(intentId, charge);
            return record(event, applied.outcome(), applied.detail());
        }));
    }

    private record Applied(PaymentGatewayEventOutcome outcome, String detail) {}

    /**
     * Credits the installment with a charge the gateway confirmed, once; it runs in the caller's transaction. When
     * the installment no longer owes it (paid some other way meanwhile, or a late fee added), the money is kept as
     * unapplied and finance is told to refund it, since partial payments and credit balances are not handled.
     */
    private Applied applyCharge(UUID intentId, GatewayPaymentIntent charge) {
        var statement = lockStatement(intentRepository.findById(intentId).orElseThrow().getAccountStatementId());
        var intent = intentRepository.findByIdForUpdate(intentId).orElseThrow();
        if (intent.isConfirmed()) {
            return new Applied(PaymentGatewayEventOutcome.DUPLICATE, "payment %s was already confirmed".formatted(intentId));
        }
        if (intent.isOpen() && intent.getProviderPaymentIntentId() == null) {
            intent.attach(charge.id());
        }
        var now = now();
        var installment = statement.findInstallment(intent.getInstallmentNumber()).orElseThrow();
        if (installment.isPaid() || !installment.isSettledBy(intent.getAmount())) {
            var reason = installment.isPaid()
                    ? "installment %d was already paid on %s".formatted(installment.getNumber(), installment.getPaidAt())
                    : "installment %d now owes %s %s".formatted(installment.getNumber(), installment.amountDue(),
                    statement.getCurrency());
            intent.markUnapplied(charge.chargeId(), now, reason);
            intentRepository.save(intent);
            notifyUnapplied(statement, intent, installment, charge);
            return new Applied(PaymentGatewayEventOutcome.UNAPPLIED, reason);
        }
        statement.creditGatewayPayment(installment.getNumber(), intent.getAmount(), now, charge.id());
        accountStatementRepository.save(statement);
        intent.succeed(charge.chargeId(), now);
        intentRepository.save(intent);
        return new Applied(PaymentGatewayEventOutcome.CREDITED, "installment %d of account statement %d"
                .formatted(installment.getNumber(), statement.getId()));
    }

    private void notifyUnapplied(AccountStatement statement, InstallmentPaymentIntent intent, Installment installment,
                                 GatewayPaymentIntent charge) {
        var reason = installment.isPaid()
                ? "la cuota ya estaba pagada desde " + installment.getPaidAt()
                : "la cuota ahora debe " + installment.amountDue() + " " + statement.getCurrency();
        notifications.enqueue("payment-unapplied:" + intent.getId(), "@finance", "Pago en línea sin aplicar",
                "Transacción: " + statement.getTransactionId() + "\nEstado de cuenta: " + statement.getId()
                        + "\nCuota: " + installment.getNumber() + "\nCobrado: " + intent.getAmount() + " "
                        + intent.getCurrency() + "\nPago en Stripe: " + charge.id() + "\nMotivo: " + reason
                        + "\nEl monto no se aplicó a la cuota: devuélvelo desde el panel de Stripe.");
    }

    private Logged recordFailure(PaymentGatewayEvent event, UUID intentId, @Nullable String message) {
        var reason = message == null || message.isBlank() ? "the payment was declined" : message;
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            var intent = intentRepository.findByIdForUpdate(intentId).orElseThrow();
            if (!intent.isOpen()) {
                return record(event, PaymentGatewayEventOutcome.FAILED,
                        "%s; payment %s was already %s".formatted(reason, intentId, intent.getStatus()));
            }
            intent.recordFailure(reason);
            intentRepository.save(intent);
            return record(event, PaymentGatewayEventOutcome.FAILED, reason);
        }));
    }

    private Logged recordCancellation(PaymentGatewayEvent event, UUID intentId) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            var intent = intentRepository.findByIdForUpdate(intentId).orElseThrow();
            if (!intent.isOpen()) {
                return record(event, PaymentGatewayEventOutcome.CANCELED,
                        "payment %s was already %s".formatted(intentId, intent.getStatus()));
            }
            intent.cancel("canceled in the gateway");
            intentRepository.save(intent);
            return record(event, PaymentGatewayEventOutcome.CANCELED, null);
        }));
    }

    /** Locks the lot and then the statement, as every change of a statement does, and reads it under the lock. */
    private AccountStatement lockStatement(Long statementId) {
        var lotId = accountStatementRepository.findById(statementId).orElseThrow().getLotId();
        lotRepository.findByIdForUpdate(lotId).orElseThrow();
        return accountStatementRepository.findByIdForUpdate(statementId).orElseThrow();
    }

    private boolean isAvailable() {
        return paymentGateway.isEnabled() && webhookVerifier.isEnabled();
    }

    /** PostgreSQL keeps microseconds: the dates answered now must equal the ones read back later. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static @Nullable UUID uuid(@Nullable String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ApplicationError unavailable() {
        return new ApplicationError("PAYMENTS_UNAVAILABLE", "Online payments are not available",
                "the payment gateway is not configured");
    }

    private static ApplicationError gatewayError() {
        return new ApplicationError("PAYMENT_GATEWAY_ERROR", "The payment gateway failed",
                "the payment gateway did not answer as expected; try again in a moment");
    }

    private static ApplicationError alreadyPaid(Installment installment) {
        return ApplicationError.conflict("installment",
                "installment %d was already paid on %s".formatted(installment.getNumber(), installment.getPaidAt()));
    }

    private static ApplicationError keyUsedElsewhere() {
        return ApplicationError.conflict("payment_intent",
                "the Idempotency-Key was already used to pay another installment; use a new one");
    }
}
