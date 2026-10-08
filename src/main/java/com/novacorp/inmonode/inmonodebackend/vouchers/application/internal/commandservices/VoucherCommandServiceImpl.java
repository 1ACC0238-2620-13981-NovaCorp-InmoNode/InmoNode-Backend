package com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RequestVoucherUploadCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherUpload;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.ReservationOperationRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.VoucherCommandService;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class VoucherCommandServiceImpl implements VoucherCommandService {

    /** US-33: the presigned URL is valid for 10 minutes. */
    static final Duration UPLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final ReservationOperationRepository operationRepository;
    private final ObjectStorage objectStorage;
    private final ExternalIamService externalIamService;

    public VoucherCommandServiceImpl(ReservationOperationRepository operationRepository, ObjectStorage objectStorage,
                                     ExternalIamService externalIamService) {
        this.operationRepository = operationRepository;
        this.objectStorage = objectStorage;
        this.externalIamService = externalIamService;
    }

    @Override
    public Result<VoucherUpload, ApplicationError> handle(RequestVoucherUploadCommand command) {
        var agentId = externalIamService.currentAgentId().orElse(null);
        if (agentId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The agent is not authenticated"));
        }
        var file = command.file();
        var ownsOperation = operationRepository.findByReservationId(file.reservationId())
                .filter(operation -> operation.isOwnedBy(agentId))
                .isPresent();
        if (!ownsOperation) {
            return Result.failure(ApplicationError.notFound("reservation_operation", file.reservationId().toString()));
        }
        var key = file.objectKey();
        var signed = objectStorage.presignUpload(key, file.contentType().mediaType(), file.sizeBytes(),
                UPLOAD_URL_VALIDITY);
        return Result.success(new VoucherUpload(signed.url(), key, signed.expiresAt(), signed.headers()));
    }
}
