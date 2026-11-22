package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationPaymentsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationPayments;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationPayments.EvidenceView;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ReservationQueryServiceImpl implements ReservationQueryService {

    /** A download link works for 10 minutes, like the upload links. */
    static final Duration DOWNLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final LotRepository lotRepository;
    private final ObjectStorage objectStorage;
    private final ExternalIamService externalIamService;

    public ReservationQueryServiceImpl(ReservationRepository reservationRepository, LotRepository lotRepository,
                                       ObjectStorage objectStorage, ExternalIamService externalIamService) {
        this.reservationRepository = reservationRepository;
        this.lotRepository = lotRepository;
        this.objectStorage = objectStorage;
        this.externalIamService = externalIamService;
    }

    @Override
    public Result<ReservationPayments, ApplicationError> handle(GetReservationPaymentsQuery query) {
        var userId = externalIamService.currentUserId().orElse(null);
        if (userId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The user is not authenticated"));
        }
        var reservation = reservationRepository.findBySourceEventId(query.transactionId())
                .filter(found -> found.getRequesterId().equals(userId))
                .orElse(null);
        if (reservation == null) {
            return Result.failure(ApplicationError.notFound("reservation", query.transactionId().toString()));
        }
        // The evidences come with their reservation (a few per reservation): the page is cut before the download
        // links are signed, so only the evidences of the page get one.
        var pagination = query.pagination();
        var evidences = reservation.getEvidences();
        var page = evidences.stream()
                .sorted(Comparator.comparing(PaymentEvidence::getSubmittedAt).thenComparing(PaymentEvidence::getId))
                .skip((long) (pagination.page() - 1) * pagination.limit())
                .limit(pagination.limit())
                .map(this::toView)
                .toList();
        return Result.success(new ReservationPayments(reservation, waitingUntil(reservation),
                new PageResult<>(page, evidences.size(), pagination.page(), pagination.limit())));
    }

    /** Only an approved evidence can be downloaded: it is the buyer's legal proof of the payment. */
    private EvidenceView toView(PaymentEvidence evidence) {
        if (evidence.getStatus() != PaymentEvidenceStatus.APPROVED || evidence.getObjectKey() == null) {
            return new EvidenceView(evidence, null, null);
        }
        var download = objectStorage.presignDownload(evidence.getObjectKey(), DOWNLOAD_URL_VALIDITY);
        return new EvidenceView(evidence, download.url(), download.expiresAt());
    }

    /** Until when its lot waits for a voucher, when the reservation still holds it. */
    private @Nullable Instant waitingUntil(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.REJECTED) return reservation.getResubmissionDeadline();
        if (reservation.getStatus() != ReservationStatus.BLOCKED) {
            return null;
        }
        return lotRepository.findById(reservation.getLotId())
                .filter(lot -> lot.getStatus() == LotStatus.BLOCKED
                        && Objects.equals(lot.getCurrentReservationId(), reservation.getId()))
                .map(Lot::getBlockedUntil)
                .orElse(null);
    }
}
