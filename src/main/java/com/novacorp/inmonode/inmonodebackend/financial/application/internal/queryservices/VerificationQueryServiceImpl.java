package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPendingVerificationsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PendingVerification;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.VerificationQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage.PresignedDownload;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class VerificationQueryServiceImpl implements VerificationQueryService {

    /** Like the other downloads: the link to the identity document photo is valid for 10 minutes. */
    static final Duration DOWNLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final LotRepository lotRepository;
    private final ObjectStorage objectStorage;

    public VerificationQueryServiceImpl(ReservationRepository reservationRepository, LotRepository lotRepository,
                                        ObjectStorage objectStorage) {
        this.reservationRepository = reservationRepository;
        this.lotRepository = lotRepository;
        this.objectStorage = objectStorage;
    }

    @Override
    public PageResult<PendingVerification> handle(GetPendingVerificationsQuery query) {
        var page = reservationRepository.findPendingEvidences(query.pagination());
        var lots = new HashMap<Long, Lot>();
        var identityDocuments = new HashMap<Long, Optional<PresignedDownload>>();
        var items = page.items().stream()
                .map(found -> {
                    var reservation = found.reservation();
                    return new PendingVerification(found.evidence(), reservation,
                            lots.computeIfAbsent(reservation.getLotId(),
                                    lotId -> lotRepository.findById(lotId).orElseThrow()),
                            identityDocuments.computeIfAbsent(reservation.getId(),
                                    id -> Optional.ofNullable(identityDocumentLink(reservation))).orElse(null));
                })
                .toList();
        return new PageResult<>(items, page.totalCount(), page.page(), page.limit());
    }

    /** The reviewer compares the photo with the buyer's data before approving the payment (US-59). */
    private @Nullable PresignedDownload identityDocumentLink(Reservation reservation) {
        var document = reservation.getIdentityDocument();
        return document == null ? null : objectStorage.presignDownload(document.objectKey(), DOWNLOAD_URL_VALIDITY);
    }
}
