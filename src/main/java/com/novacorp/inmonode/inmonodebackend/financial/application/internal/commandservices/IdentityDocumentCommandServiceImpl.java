package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AttachIdentityDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestIdentityDocumentUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocumentRegistration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocumentUpload;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.IdentityDocumentCommandService;
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
public class IdentityDocumentCommandServiceImpl implements IdentityDocumentCommandService {

    /** Like the voucher uploads: the presigned URL is valid for 10 minutes. */
    static final Duration UPLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final ObjectStorage objectStorage;
    private final ExternalIamService externalIamService;
    private final Clock clock;

    public IdentityDocumentCommandServiceImpl(ReservationRepository reservationRepository, ObjectStorage objectStorage,
                                              ExternalIamService externalIamService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.objectStorage = objectStorage;
        this.externalIamService = externalIamService;
        this.clock = clock;
    }

    @Override
    public Result<IdentityDocumentUpload, ApplicationError> handle(RequestIdentityDocumentUploadCommand command) {
        var document = command.document();
        return ownFieldReservation(document.transactionId()).flatMap(reservation -> {
            if (reservation.getIdentityDocument() != null) {
                return Result.failure(alreadyRegistered(document.transactionId()));
            }
            var key = document.objectKey();
            var signed = objectStorage.presignUpload(key, document.contentType(), document.sizeBytes(),
                    UPLOAD_URL_VALIDITY);
            return Result.success(new IdentityDocumentUpload(signed.url(), key, signed.expiresAt(), signed.headers()));
        });
    }

    @Override
    @Transactional
    public Result<IdentityDocumentRegistration, ApplicationError> handle(AttachIdentityDocumentCommand command) {
        var document = command.document();
        return ownFieldReservation(document.transactionId()).flatMap(found -> {
            var reservation = reservationRepository.findByIdForUpdate(Objects.requireNonNull(found.getId()))
                    .orElseThrow();
            var attached = reservation.getIdentityDocument();
            if (attached != null && !attached.documentId().equals(document.documentId())) {
                return Result.failure(alreadyRegistered(document.transactionId()));
            }
            if (attached != null) {
                return Result.success(new IdentityDocumentRegistration(attached,
                        Objects.requireNonNull(reservation.getIdentityDocumentAttachedAt()),
                        IdentityDocumentRegistration.Result.DUPLICATE));
            }
            if (!isUploaded(document)) {
                return Result.failure(new ApplicationError("IDENTITY_DOCUMENT_FILE_NOT_UPLOADED",
                        "The identity document photo is not in the file repository",
                        "no %s file of %d bytes at %s; upload it with a new URL and register it again"
                                .formatted(document.contentType(), document.sizeBytes(), document.objectKey())));
            }
            // PostgreSQL keeps microseconds: the date answered now must equal the one a re-send reads back.
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            reservation.attachIdentityDocument(document, now);
            reservationRepository.save(reservation);
            return Result.success(new IdentityDocumentRegistration(document, now,
                    IdentityDocumentRegistration.Result.RECEIVED));
        });
    }

    /** The field reservation, when the caller is the agent who synchronized it; 404 otherwise, as for vouchers. */
    private Result<Reservation, ApplicationError> ownFieldReservation(UUID transactionId) {
        var agentId = externalIamService.currentUserId().orElse(null);
        if (agentId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The user is not authenticated"));
        }
        return reservationRepository.findBySourceEventId(transactionId)
                .filter(reservation -> reservation.getChannel() == ReservationChannel.FIELD)
                .filter(reservation -> agentId.equals(reservation.getRequesterId()))
                .<Result<Reservation, ApplicationError>>map(Result::success)
                .orElseGet(() -> Result.failure(ApplicationError.notFound("reservation", transactionId.toString())));
    }

    private static ApplicationError alreadyRegistered(UUID transactionId) {
        return ApplicationError.conflict("identity_document",
                "the identity document of reservation %s is already registered; its photo cannot be replaced"
                        .formatted(transactionId));
    }

    /** The signature fixes type and size, but the client declares them again here, so both are checked. */
    private boolean isUploaded(IdentityDocument document) {
        return objectStorage.describe(document.objectKey())
                .filter(stored -> stored.sizeBytes() == document.sizeBytes())
                .map(ObjectStorage.StoredObject::contentType)
                .filter(contentType -> contentType.strip().equalsIgnoreCase(document.contentType()))
                .isPresent();
    }
}
