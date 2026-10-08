package com.novacorp.inmonode.inmonodebackend.catalog.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.SyncFieldRecordsCommand.ReservationData;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.valueobjects.ReservationSyncOutcome;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.valueobjects.ReservationSyncOutcome.Result;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Anti-corruption layer towards Control Financiero y Documental, the only authority on lot availability: hands it a
 * reservation made offline and translates its answer into what the field app shows.
 */
@Service
public class ExternalFinancialService {

    private final FieldReservationConsolidationFacade consolidationFacade;

    public ExternalFinancialService(FieldReservationConsolidationFacade consolidationFacade) {
        this.consolidationFacade = consolidationFacade;
    }

    /** Consolidated in its own transaction: a conflict here never undoes the other reservations of the sync. */
    public ReservationSyncOutcome consolidate(Long agentId, ReservationData reservation) {
        var answer = consolidationFacade.consolidate(reservation.reservationId(), reservation.lotId(), agentId,
                reservation.prospectId(), reservation.initialAmount(), reservation.reservedAt());
        return new ReservationSyncOutcome(reservation.reservationId(), Result.valueOf(answer.result()),
                answer.reservationStatus(), answer.blockedUntil(), answer.conflictReason(),
                resultOrNull(answer.originalResult()));
    }

    private static @Nullable Result resultOrNull(@Nullable String result) {
        return result == null ? null : Result.valueOf(result);
    }
}
