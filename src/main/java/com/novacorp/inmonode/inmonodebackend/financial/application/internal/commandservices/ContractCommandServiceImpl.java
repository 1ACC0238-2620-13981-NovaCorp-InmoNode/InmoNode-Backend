package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalQuotationSnapshotService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AcknowledgeContractCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.IssueContractCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestContractUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractUpload;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class ContractCommandServiceImpl implements ContractCommandService {

    /** Like the voucher uploads: the presigned URL is valid for 10 minutes. */
    static final Duration UPLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final AccountStatementRepository accountStatementRepository;
    private final ObjectStorage objectStorage;
    private final ExternalIamService externalIamService;
    private final Clock clock;
    private final LotRepository lotRepository;
    private final ExternalQuotationSnapshotService quotationSnapshots;
    private final com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.FinancialNotificationOutbox notifications;

    public ContractCommandServiceImpl(ReservationRepository reservationRepository,
                                      ContractRepository contractRepository,
                                      AccountStatementRepository accountStatementRepository,
                                      ObjectStorage objectStorage, ExternalIamService externalIamService,
                                      Clock clock, LotRepository lotRepository, ExternalQuotationSnapshotService quotationSnapshots,
            com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.FinancialNotificationOutbox notifications) {
        this.reservationRepository = reservationRepository;
        this.contractRepository = contractRepository;
        this.accountStatementRepository = accountStatementRepository;
        this.objectStorage = objectStorage;
        this.externalIamService = externalIamService;
        this.clock = clock;
        this.lotRepository = lotRepository;
        this.quotationSnapshots = quotationSnapshots;
        this.notifications = notifications;
    }

    @Override
    public Result<ContractUpload, ApplicationError> handle(RequestContractUploadCommand command) {
        var document = new ContractDocument(command.transactionId(), command.documentId(), command.sizeBytes());
        return issuable(command.transactionId()).map(reservation -> {
            var key = document.objectKey();
            var signed = objectStorage.presignUpload(key, ContractDocument.CONTENT_TYPE, document.sizeBytes(),
                    UPLOAD_URL_VALIDITY);
            return new ContractUpload(signed.url(), key, signed.expiresAt(), signed.headers());
        });
    }

    @Override
    @Transactional
    public Result<Contract, ApplicationError> handle(IssueContractCommand command) {
        var issuerId = externalIamService.currentUserId().orElse(null);
        if (issuerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The issuer is not authenticated"));
        }
        var document = new ContractDocument(command.transactionId(), command.documentId(), command.sizeBytes());
        var found = reservationRepository.findBySourceEventId(command.transactionId()).orElse(null);
        if (found == null) return Result.failure(ApplicationError.notFound("reservation", command.transactionId().toString()));
        lotRepository.findByIdForUpdate(found.getLotId()).orElseThrow();
        reservationRepository.findByIdForUpdate(found.getId()).orElseThrow();
        return issuable(command.transactionId()).flatMap(reservation -> {
            if (!isUploaded(document)) {
                return Result.failure(new ApplicationError("CONTRACT_FILE_NOT_UPLOADED",
                        "The contract file is not in the file repository",
                        "no PDF of %d bytes at %s; upload it with a new URL and issue the contract again"
                                .formatted(document.sizeBytes(), document.objectKey())));
            }
            // PostgreSQL keeps microseconds: the dates answered now must equal the ones read back later.
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            var saved = contractRepository.save(Contract.issue(reservation, document, issuerId, now));
            openAccountStatement(saved, now);
            return Result.success(saved);
        });
    }

    @Override
    @Transactional
    public Result<Contract, ApplicationError> handle(AcknowledgeContractCommand command) {
        var buyerId = externalIamService.currentUserId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        var contract = contractRepository.findById(command.contractId())
                .filter(found -> found.belongsTo(buyerId))
                .orElse(null);
        if (contract == null) {
            return Result.failure(ApplicationError.notFound("contract", String.valueOf(command.contractId())));
        }
        lotRepository.findByIdForUpdate(contract.getLotId()).orElseThrow();
        contract = contractRepository.findByIdForUpdate(command.contractId()).orElseThrow();
        // PostgreSQL keeps microseconds: the date answered now must equal the one read back later.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!contract.registerBuyerAcknowledgment(buyerId, now)) {
            return Result.success(contract);
        }
        var saved = contractRepository.save(contract);
        notifications.enqueue("contract-acknowledged:" + contract.getId(), "@legal", "Conformidad preliminar del contrato",
                "Contrato: " + contract.getId() + "\nTransacción: " + contract.getTransactionId()
                        + "\nComprador: " + buyerId + "\nConformidad registrada: " + saved.getBuyerAcknowledgedAt()
                        + "\nLa conformidad preliminar no acredita una firma electrónica del proveedor.");
        return Result.success(saved);
    }

    /**
     * US-23: issuance opens the buyer's account statement, in the same transaction. Web reservations made before
     * the financing plan was kept have none to schedule, so they get no statement.
     */
    private void openAccountStatement(Contract contract, Instant now) {
        var contractId = Objects.requireNonNull(contract.getId());
        if (accountStatementRepository.findByContractId(contractId).isPresent()) {
            return;
        }
        reservationRepository.findById(contract.getReservationId())
                .filter(reservation -> reservation.getFinancingPlan() != null)
                .ifPresent(reservation ->
                        accountStatementRepository.save(reservation.getFinancingPlan().quotationId() == null
                                ? AccountStatement.open(contract, reservation, now)
                                : AccountStatement.open(contract, reservation, now, quotationSnapshots.scheduleFor(reservation,
                                        java.time.LocalDate.ofInstant(now, AccountStatement.SALES_ZONE)))));
    }

    /** The reservation, when it can get its contract now. */
    private Result<Reservation, ApplicationError> issuable(UUID transactionId) {
        var reservation = reservationRepository.findBySourceEventId(transactionId).orElse(null);
        if (reservation == null) {
            return Result.failure(ApplicationError.notFound("reservation", transactionId.toString()));
        }
        var obstacle = Contract.issuingObstacle(reservation);
        if (obstacle.isPresent()) {
            return Result.failure(ApplicationError.businessRuleViolation("contract-issuing", obstacle.get()));
        }
        if (contractRepository.findByReservationId(Objects.requireNonNull(reservation.getId())).isPresent()) {
            return Result.failure(ApplicationError.conflict("contract",
                    "the contract of reservation %s was already issued".formatted(transactionId)));
        }
        return Result.success(reservation);
    }

    private boolean isUploaded(ContractDocument document) {
        return objectStorage.describe(document.objectKey())
                .filter(stored -> stored.sizeBytes() == document.sizeBytes())
                .map(ObjectStorage.StoredObject::contentType)
                .filter(contentType -> ContractDocument.CONTENT_TYPE.equalsIgnoreCase(contentType.strip()))
                .isPresent();
    }
}
