package com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage.StoredObject;
import com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RegisterVoucherCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RequestVoucherUploadCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherFile;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherRegistration;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherUpload;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.ReservationOperationRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.VoucherRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.VoucherCommandService;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.events.PaymentVoucherReceivedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoucherCommandServiceImpl implements VoucherCommandService {

    /** US-33: the presigned URL is valid for 10 minutes. */
    static final Duration UPLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final ReservationOperationRepository operationRepository;
    private final VoucherRepository voucherRepository;
    private final ObjectStorage objectStorage;
    private final ExternalIamService externalIamService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public VoucherCommandServiceImpl(ReservationOperationRepository operationRepository,
                                     VoucherRepository voucherRepository, ObjectStorage objectStorage,
                                     ExternalIamService externalIamService, ApplicationEventPublisher eventPublisher,
                                     Clock clock) {
        this.operationRepository = operationRepository;
        this.voucherRepository = voucherRepository;
        this.objectStorage = objectStorage;
        this.externalIamService = externalIamService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Result<VoucherUpload, ApplicationError> handle(RequestVoucherUploadCommand command) {
        var file = command.file();
        var denied = checkOwnership(file.reservationId());
        if (denied.isPresent()) {
            return Result.failure(denied.get());
        }
        if (voucherRepository.existsByVoucherId(file.voucherId())) {
            return Result.failure(ApplicationError.conflict("voucher",
                    "voucher %s is already registered; its file cannot be replaced".formatted(file.voucherId())));
        }
        var key = file.objectKey();
        var signed = objectStorage.presignUpload(key, file.contentType().mediaType(), file.sizeBytes(),
                UPLOAD_URL_VALIDITY);
        return Result.success(new VoucherUpload(signed.url(), key, signed.expiresAt(), signed.headers()));
    }

    /**
     * Transactional with the "Comprobante de pago recibido" listeners: the voucher and what they store with it are
     * kept together, or none of it is.
     */
    @Override
    @Transactional
    public Result<VoucherRegistration, ApplicationError> handle(RegisterVoucherCommand command) {
        var file = command.file();
        var denied = checkOwnership(file.reservationId());
        if (denied.isPresent()) {
            return Result.failure(denied.get());
        }
        var registered = voucherRepository.findByVoucherId(file.voucherId());
        if (registered.isPresent()) {
            return registered.get().belongsTo(file.reservationId())
                    ? Result.success(new VoucherRegistration(registered.get(), VoucherRegistration.Result.DUPLICATE))
                    : Result.failure(ApplicationError.conflict("voucher",
                            "voucher %s is already registered for another reservation".formatted(file.voucherId())));
        }
        // PostgreSQL keeps microseconds: the receipt answered now must equal the one a re-send reads back.
        var receivedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var received = Voucher.receive(file, command.extractedData(), command.manuallyCorrected(), receivedAt);
        if (!isUploaded(file)) {
            return Result.failure(new ApplicationError("VOUCHER_FILE_NOT_UPLOADED",
                    "The voucher file is not in the file repository",
                    "no %s file of %d bytes at %s; upload it with a new URL and register the voucher again"
                            .formatted(file.contentType().mediaType(), file.sizeBytes(), file.objectKey())));
        }
        var voucher = voucherRepository.save(received);
        eventPublisher.publishEvent(toPaymentVoucherReceived(voucher));
        return Result.success(new VoucherRegistration(voucher, VoucherRegistration.Result.RECEIVED));
    }

    /** Empty when the reservation is an operation of the authenticated agent. */
    private Optional<ApplicationError> checkOwnership(UUID reservationId) {
        var agentId = externalIamService.currentAgentId().orElse(null);
        if (agentId == null) {
            return Optional.of(new ApplicationError("UNAUTHORIZED", "The agent is not authenticated"));
        }
        var ownsOperation = operationRepository.findByReservationId(reservationId)
                .filter(operation -> operation.isOwnedBy(agentId))
                .isPresent();
        return ownsOperation
                ? Optional.empty()
                : Optional.of(ApplicationError.notFound("reservation_operation", reservationId.toString()));
    }

    /** The signature fixes type and size, but the client declares them again here, so both are checked. */
    private boolean isUploaded(VoucherFile file) {
        return objectStorage.describe(file.objectKey())
                .filter(stored -> stored.sizeBytes() == file.sizeBytes())
                .map(StoredObject::contentType)
                .filter(contentType -> contentType.strip().equalsIgnoreCase(file.contentType().mediaType()))
                .isPresent();
    }

    private static PaymentVoucherReceivedEvent toPaymentVoucherReceived(Voucher voucher) {
        var data = voucher.getExtractedData();
        return new PaymentVoucherReceivedEvent(voucher.getVoucherId(), voucher.getReservationId(), data.amount(),
                data.currency(), data.operationDate(), data.operationCode(), voucher.isManuallyCorrected(),
                voucher.getObjectKey(), voucher.getReceivedAt());
    }
}
