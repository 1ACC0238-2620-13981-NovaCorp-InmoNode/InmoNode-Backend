package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPendingVerificationsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PendingVerification;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.VerificationQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class VerificationQueryServiceImpl implements VerificationQueryService {

    private final ReservationRepository reservationRepository;
    private final LotRepository lotRepository;

    public VerificationQueryServiceImpl(ReservationRepository reservationRepository, LotRepository lotRepository) {
        this.reservationRepository = reservationRepository;
        this.lotRepository = lotRepository;
    }

    @Override
    public List<PendingVerification> handle(GetPendingVerificationsQuery query) {
        var lots = new HashMap<Long, Lot>();
        return reservationRepository.findWithPendingEvidence().stream()
                .flatMap(reservation -> reservation.getEvidences().stream()
                        .filter(PaymentEvidence::isPending)
                        .map(evidence -> new PendingVerification(evidence, reservation,
                                lots.computeIfAbsent(reservation.getLotId(),
                                        lotId -> lotRepository.findById(lotId).orElseThrow()))))
                .sorted(Comparator.comparing(pending -> pending.evidence().getSubmittedAt()))
                .toList();
    }
}
