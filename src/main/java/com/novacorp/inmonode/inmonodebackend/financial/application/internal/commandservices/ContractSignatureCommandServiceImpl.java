package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.FinancialNotificationOutbox;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.InvalidSignatureCallbackException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureEvent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureInvitation;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProvider;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProviderException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSheet;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSubmission;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureWebhookVerifier;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveSignatureEventCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestContractSignatureCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.SendDueSignatureRequestsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignatureDelivery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignatureProgress;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureDeliveryStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureEventOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureEventRecord;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignedContractDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractSignatureDeliveryRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.SignatureEventRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractSignatureCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * US-56. An issued contract is queued in the same transaction that issues it; this service sends it to the
 * provider, retrying with growing waits, and registers the signature only when the provider's API confirms what its
 * callback says.
 * <p>
 * The provider is never called while the lot or the contract are locked. A sender locks only the request it sends,
 * and records the submission afterwards; the callback downloads and stores the signed sheet first, and only then
 * takes the locks, lot first and contract second, to sign the contract and sell the lot.
 */
@Service
public class ContractSignatureCommandServiceImpl implements ContractSignatureCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(ContractSignatureCommandServiceImpl.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** The provider requires an email; this one is never written to, since it sends no email. */
    private static final String UNKNOWN_EMAIL = "comprador-%d@inmonode.invalid";

    private final ContractRepository contractRepository;
    private final ContractSignatureDeliveryRepository deliveries;
    private final SignatureEventRepository events;
    private final LotRepository lotRepository;
    private final ProjectRepository projectRepository;
    private final SignatureProvider provider;
    private final SignatureWebhookVerifier verifier;
    private final ObjectStorage objectStorage;
    private final FinancialNotificationOutbox notifications;
    private final ExternalIamService externalIamService;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ContractSignatureCommandServiceImpl(ContractRepository contractRepository,
                                               ContractSignatureDeliveryRepository deliveries,
                                               SignatureEventRepository events, LotRepository lotRepository,
                                               ProjectRepository projectRepository, SignatureProvider provider,
                                               SignatureWebhookVerifier verifier, ObjectStorage objectStorage,
                                               FinancialNotificationOutbox notifications,
                                               ExternalIamService externalIamService,
                                               PlatformTransactionManager transactionManager, Clock clock) {
        this.contractRepository = contractRepository;
        this.deliveries = deliveries;
        this.events = events;
        this.lotRepository = lotRepository;
        this.projectRepository = projectRepository;
        this.provider = provider;
        this.verifier = verifier;
        this.objectStorage = objectStorage;
        this.notifications = notifications;
        this.externalIamService = externalIamService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ---------------------------------------------------------------- the back office sends it again

    @Override
    public Result<ContractSignatureProgress, ApplicationError> handle(RequestContractSignatureCommand command) {
        if (externalIamService.currentUserId().isEmpty()) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The back office is not authenticated"));
        }
        var contract = contractRepository.findById(command.contractId()).orElse(null);
        if (contract == null) {
            return Result.failure(ApplicationError.notFound("contract", String.valueOf(command.contractId())));
        }
        if (contract.isSigned()) {
            return Result.failure(new ApplicationError("SIGNATURE_CONFLICT", "The contract is already signed",
                    "contract %d was signed on %s".formatted(contract.getId(), contract.getSignedAt())));
        }
        var delivery = deliveries.findByContractId(command.contractId()).orElse(null);
        if (contract.isAwaitingSignature()
                || (delivery != null && delivery.status() == SignatureDeliveryStatus.PENDING)) {
            return Result.success(new ContractSignatureProgress(contract, delivery));
        }
        String hash = contract.getDocumentSha256();
        if (hash == null) {
            var pdf = objectStorage.read(contract.getObjectKey()).orElse(null);
            if (pdf == null) {
                return Result.failure(new ApplicationError("CONTRACT_FILE_NOT_UPLOADED",
                        "The contract file is not in the file repository",
                        "no PDF at %s; its hash is needed for the signature sheet".formatted(contract.getObjectKey())));
            }
            hash = SignedContractDocument.sha256Of(pdf);
        }
        var documentSha256 = hash;
        return Result.success(Objects.requireNonNull(transactions.execute(status -> {
            var locked = contractRepository.findByIdForUpdate(command.contractId()).orElseThrow();
            if (locked.getDocumentSha256() == null) {
                locked.attachDocumentSha256(documentSha256);
                locked = contractRepository.save(locked);
            }
            var queued = ContractSignatureDelivery.queue(command.contractId(), now());
            deliveries.save(queued);
            LOG.info("Contract {} was queued again to be sent to signature", command.contractId());
            return new ContractSignatureProgress(locked, queued);
        })));
    }

    // ---------------------------------------------------------------- the job sends the due requests

    @Override
    public int handle(SendDueSignatureRequestsCommand command) {
        if (!provider.isEnabled()) {
            return 0;
        }
        var sent = 0;
        for (var contractId : deliveries.findDueContractIds(now(), command.limit())) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> send(contractId)))) {
                    sent++;
                }
            } catch (RuntimeException failure) {
                LOG.error("Could not send contract {} to signature ({})", contractId, failure.getClass().getSimpleName());
            }
        }
        return sent;
    }

    /** Sends one request while holding its lock, so no other sender takes it meanwhile. */
    private boolean send(Long contractId) {
        var now = now();
        var delivery = deliveries.lockIfDue(contractId, now).orElse(null);
        if (delivery == null) {
            return false;
        }
        var contract = contractRepository.findById(contractId).orElseThrow();
        if (!contract.canBeSentToSignature()) {
            deliveries.save(delivery.sent(now));
            return false;
        }
        if (contract.getDocumentSha256() == null) {
            fail(contract, delivery, "the contract has no SHA-256 of its PDF", now);
            return false;
        }
        var sheet = sheetOf(contract);
        SignatureInvitation invitation;
        try {
            invitation = provider.requestSignature(sheet);
        } catch (RuntimeException failure) {
            var reason = failure instanceof SignatureProviderException ? failure.getMessage()
                    : failure.getClass().getSimpleName();
            fail(contract, delivery, reason, now);
            return false;
        }
        var locked = contractRepository.findByIdForUpdate(contractId).orElseThrow();
        if (locked.requestSignature(invitation.submissionId(), invitation.signingUrl(), now)) {
            contractRepository.save(locked);
            tellTheBuyer(locked, sheet, invitation.signingUrl());
        }
        deliveries.save(delivery.sent(now));
        LOG.info("Contract {} was sent to signature in submission {}", contractId, invitation.submissionId());
        return true;
    }

    private void fail(Contract contract, ContractSignatureDelivery delivery, String reason, Instant now) {
        var failed = delivery.failedAttempt(reason, now);
        deliveries.save(failed);
        if (failed.isExhausted()) {
            LOG.error("Contract {} could not be sent to signature after {} attempts; the back office was told",
                    contract.getId(), failed.attempts());
            notifications.enqueue("contract-signature-failed:%d:%d".formatted(contract.getId(),
                            failed.requestedAt().toEpochMilli()), "@finance",
                    "Firma electrónica: no se pudo enviar el contrato N.° " + contract.getId(),
                    "Contrato: N.° " + contract.getId() + "\nTransacción: " + contract.getTransactionId()
                            + "\nIntentos: " + failed.attempts() + "\nÚltimo error: " + failed.lastError()
                            + "\nRevise la configuración de DocuSeal y vuelva a enviarlo con "
                            + "POST /api/v1/contracts/" + contract.getId() + "/signature-request.");
        } else {
            LOG.warn("Attempt {} to send contract {} to signature failed; next one at {}", failed.attempts(),
                    contract.getId(), failed.nextAttemptAt());
        }
    }

    private SignatureSheet sheetOf(Contract contract) {
        var id = Objects.requireNonNull(contract.getId());
        var email = externalIamService.emailOf(contract.getBuyerId()).orElse(UNKNOWN_EMAIL.formatted(contract.getBuyerId()));
        var buyer = contract.getCoOwner() == null ? email
                : email + " y " + contract.getCoOwner().fullName() + " (co-titular)";
        var lot = lotRepository.findById(contract.getLotId())
                .map(found -> "Lote " + found.getCode() + projectRepository.findById(found.getProjectId())
                        .map(project -> " - " + project.getName()).orElse(""))
                .orElse("Lote " + contract.getLotId());
        var issuedOn = LocalDate.ofInstant(contract.getIssuedAt(), AccountStatement.SALES_ZONE).format(DAY);
        return new SignatureSheet(id, email, "N.° " + id, buyer, lot, issuedOn,
                Objects.requireNonNull(contract.getDocumentSha256()));
    }

    private void tellTheBuyer(Contract contract, SignatureSheet sheet, String signingUrl) {
        var email = externalIamService.emailOf(contract.getBuyerId()).orElse(null);
        if (email == null) {
            return;
        }
        notifications.enqueue("contract-signature-link:%d:%d".formatted(contract.getId(),
                        contract.getSignatureSubmissionId()), email,
                "Firma tu contrato N.° " + contract.getId(),
                "Tu contrato N.° " + contract.getId() + " (" + sheet.lot() + ") está listo para firmar.\n"
                        + "Léelo en la app y fírmalo aquí: " + signingUrl + "\n"
                        + "La hoja de firma cita la huella SHA-256 del contrato: " + sheet.documentSha256() + "\n"
                        + "El enlace es personal: no lo compartas.");
    }

    // ---------------------------------------------------------------- the provider's callback

    @Override
    public Result<SignatureEventRecord, ApplicationError> handle(ReceiveSignatureEventCommand command) {
        if (!verifier.isEnabled()) {
            return Result.failure(new ApplicationError("SIGNATURES_UNAVAILABLE",
                    "The signature provider is not configured"));
        }
        SignatureEvent event;
        try {
            event = verifier.verify(command.payload(), command.signature());
        } catch (InvalidSignatureCallbackException invalid) {
            LOG.warn("Rejected a callback that is not signed by the signature provider: {}", invalid.getMessage());
            return Result.failure(new ApplicationError("INVALID_WEBHOOK_SIGNATURE",
                    "The callback is not signed by the signature provider"));
        }
        // A redelivered callback gets its first outcome; a rejected one is checked again, since the provider may
        // have caught up since then.
        var known = events.findByKey(event.eventKey());
        if (known.isPresent() && known.get().outcome() != SignatureEventOutcome.REJECTED) {
            return Result.success(known.get());
        }
        Logged logged;
        try {
            logged = process(event);
        } catch (SignatureProviderException failure) {
            LOG.warn("Could not confirm the signature callback {} with the provider: {}", event.eventKey(),
                    failure.getMessage());
            return Result.failure(new ApplicationError("SIGNATURE_PROVIDER_ERROR",
                    "The signature provider could not confirm the callback"));
        }
        var record = logged.answer();
        if (!logged.first()) {
            return Result.success(record);
        }
        if (record.outcome() == SignatureEventOutcome.REJECTED) {
            LOG.warn("Signature callback {} was rejected: {}", record.eventKey(), record.detail());
        } else {
            LOG.info("Signature callback {} of contract {}: {}", record.eventKey(), record.contractId(), record.outcome());
        }
        return Result.success(record);
    }

    /** What a callback answers, and whether this delivery is the one that logged it. */
    private record Logged(SignatureEventRecord answer, boolean first) {
    }

    private Logged process(SignatureEvent event) {
        if (event.kind() == SignatureEvent.Kind.OTHER) {
            return record(event, null, SignatureEventOutcome.IGNORED, "this type changes no contract");
        }
        var contractId = SignatureProvider.contractIdOf(event.externalId()).orElse(null);
        if (contractId == null || event.submissionId() == null) {
            return record(event, null, SignatureEventOutcome.IGNORED, "not about a contract of InmoNode");
        }
        var contract = contractRepository.findById(contractId).orElse(null);
        if (contract == null || !Objects.equals(contract.getSignatureSubmissionId(), event.submissionId())) {
            return record(event, contractId, SignatureEventOutcome.IGNORED,
                    "the contract does not wait for this submission");
        }
        return event.kind() == SignatureEvent.Kind.COMPLETED ? sign(contract, event) : decline(contract, event);
    }

    /**
     * Scenario 1: the API confirms the signature, the signed sheet is kept, and only then the contract changes. The
     * outcome is logged in the same transaction, so a delivery that waited for the lock answers it.
     */
    private Logged sign(Contract contract, SignatureEvent event) {
        var submissionId = Objects.requireNonNull(event.submissionId());
        if (contract.isSigned()) {
            return record(event, contract.getId(), SignatureEventOutcome.DUPLICATE, "already signed");
        }
        var submission = provider.fetchSubmission(submissionId);
        var rejection = rejectionOf(contract, submission, SignatureSubmission.Status.COMPLETED);
        if (rejection != null) {
            return record(event, contract.getId(), SignatureEventOutcome.REJECTED, rejection);
        }
        var pdf = provider.downloadSignedDocument(submission);
        if (!SignedContractDocument.isPdf(pdf)) {
            return record(event, contract.getId(), SignatureEventOutcome.REJECTED, "the signed document is not a PDF");
        }
        var key = SignedContractDocument.objectKeyFor(contract.getTransactionId(), contract.getDocumentId());
        objectStorage.store(key, SignedContractDocument.CONTENT_TYPE, pdf);
        var document = new SignedContractDocument(key, pdf.length, SignedContractDocument.sha256Of(pdf));
        var signedAt = (submission.completedAt() == null ? now() : submission.completedAt()).truncatedTo(ChronoUnit.MICROS);
        return transactions.execute(status -> {
            var lot = lotRepository.findByIdForUpdate(contract.getLotId()).orElseThrow();
            var locked = contractRepository.findByIdForUpdate(Objects.requireNonNull(contract.getId())).orElseThrow();
            boolean signedNow;
            try {
                signedNow = locked.registerSignature(submissionId, document, signedAt);
            } catch (IllegalStateException conflict) {
                return record(event, locked.getId(), SignatureEventOutcome.REJECTED, conflict.getMessage());
            }
            if (!signedNow) {
                return record(event, locked.getId(), SignatureEventOutcome.DUPLICATE, "already signed");
            }
            contractRepository.save(locked);
            var sold = lot.markSold(locked.getReservationId());
            if (sold) {
                lotRepository.save(lot);
            } else {
                LOG.warn("Contract {} was signed but lot {} is {}, not reserved for its reservation; it was not sold",
                        locked.getId(), lot.getId(), lot.getStatus());
            }
            notifications.enqueue("contract-signed:" + locked.getId(), "@legal",
                    "Contrato N.° " + locked.getId() + " firmado electrónicamente",
                    "Contrato: N.° " + locked.getId() + "\nTransacción: " + locked.getTransactionId()
                            + "\nComprador: " + locked.getBuyerId() + "\nFirmado: " + signedAt
                            + "\nSHA-256 del contrato: " + locked.getDocumentSha256()
                            + "\nSHA-256 de la hoja firmada: " + document.sha256()
                            + "\nLote: " + (sold ? "vendido" : "sin cambios (" + lot.getStatus() + ")"));
            return record(event, locked.getId(), SignatureEventOutcome.SIGNED,
                    sold ? "signed; the lot is sold" : "signed; the lot was " + lot.getStatus());
        });
    }

    private Logged decline(Contract contract, SignatureEvent event) {
        var submissionId = Objects.requireNonNull(event.submissionId());
        var submission = provider.fetchSubmission(submissionId);
        var rejection = rejectionOf(contract, submission, SignatureSubmission.Status.DECLINED);
        if (rejection != null) {
            return record(event, contract.getId(), SignatureEventOutcome.REJECTED, rejection);
        }
        var reason = submission.declineReason() != null ? submission.declineReason() : event.declineReason();
        var at = now();
        return transactions.execute(status -> {
            var locked = contractRepository.findByIdForUpdate(Objects.requireNonNull(contract.getId())).orElseThrow();
            boolean declinedNow;
            try {
                declinedNow = locked.declineSignature(submissionId, reason, at);
            } catch (IllegalStateException conflict) {
                return record(event, locked.getId(), SignatureEventOutcome.REJECTED, conflict.getMessage());
            }
            if (!declinedNow) {
                return record(event, locked.getId(), SignatureEventOutcome.DUPLICATE, "already declined");
            }
            contractRepository.save(locked);
            notifications.enqueue("contract-signature-declined:%d:%d".formatted(locked.getId(), submissionId),
                    "@finance", "Firma electrónica rechazada: contrato N.° " + locked.getId(),
                    "Contrato: N.° " + locked.getId() + "\nTransacción: " + locked.getTransactionId()
                            + "\nComprador: " + locked.getBuyerId() + "\nMotivo: "
                            + (locked.getSignatureDeclineReason() == null ? "(sin motivo)" : locked.getSignatureDeclineReason())
                            + "\nPara enviarlo otra vez: POST /api/v1/contracts/" + locked.getId() + "/signature-request.");
            return record(event, locked.getId(), SignatureEventOutcome.DECLINED, "declined by the buyer");
        });
    }

    /** Why the provider's API does not confirm the callback; {@code null} when it does. */
    private static String rejectionOf(Contract contract, SignatureSubmission submission,
                                      SignatureSubmission.Status expected) {
        if (submission.status() != expected) {
            return "the provider says the submission is %s, not %s".formatted(submission.status(), expected);
        }
        var reference = SignatureProvider.externalIdOf(Objects.requireNonNull(contract.getId()));
        if (!reference.equals(submission.externalId())) {
            return "the submission belongs to %s, not to %s".formatted(submission.externalId(), reference);
        }
        return null;
    }

    /**
     * Logs what a callback did; inside the transaction that did it when there is one, so the outcome kept for a
     * callback is always the one of the delivery that applied it. A delivery that finds it logged answers what was
     * kept.
     */
    private Logged record(SignatureEvent event, Long contractId, SignatureEventOutcome outcome, String detail) {
        var record = new SignatureEventRecord(event.eventKey(), event.type(), event.submissionId(), contractId, outcome,
                detail, now());
        if (events.record(record)) {
            return new Logged(record, true);
        }
        return new Logged(events.findByKey(record.eventKey()).orElse(record), false);
    }

    /** PostgreSQL keeps microseconds: the dates answered now must equal the ones read back later. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
