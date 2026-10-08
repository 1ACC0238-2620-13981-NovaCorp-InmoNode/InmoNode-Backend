package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AcknowledgeContractCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.IssueContractCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestContractUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractUpload;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class ContractCommandServiceImpl implements ContractCommandService {

    /** Like the voucher uploads: the presigned URL is valid for 10 minutes. */
    static final Duration UPLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final ObjectStorage objectStorage;
    private final ExternalIamService externalIamService;
    private final Clock clock;

    public ContractCommandServiceImpl(ReservationRepository reservationRepository,
                                      ContractRepository contractRepository, ObjectStorage objectStorage,
                                      ExternalIamService externalIamService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.contractRepository = contractRepository;
        this.objectStorage = objectStorage;
        this.externalIamService = externalIamService;
        this.clock = clock;
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
        return issuable(command.transactionId()).flatMap(reservation -> {
            if (!isUploaded(document)) {
                return Result.failure(new ApplicationError("CONTRACT_FILE_NOT_UPLOADED",
                        "The contract file is not in the file repository",
                        "no PDF of %d bytes at %s; upload it with a new URL and issue the contract again"
                                .formatted(document.sizeBytes(), document.objectKey())));
            }
            // PostgreSQL keeps microseconds: the dates answered now must equal the ones read back later.
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            return Result.success(contractRepository.save(Contract.issue(reservation, document, issuerId, now)));
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
        // PostgreSQL keeps microseconds: the date answered now must equal the one read back later.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return Result.success(contract.registerBuyerAcknowledgment(buyerId, now)
                ? contractRepository.save(contract)
                : contract);
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
