package com.novacorp.inmonode.inmonodebackend.financial.application.acl;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ConsolidateFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FieldReservationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidation;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class FieldReservationConsolidationFacadeImpl implements FieldReservationConsolidationFacade {

    private final ReservationCommandService reservationCommandService;
    private final com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository lots;

    public FieldReservationConsolidationFacadeImpl(ReservationCommandService reservationCommandService,
            com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository lots) {
        this.reservationCommandService = reservationCommandService;
        this.lots = lots;
    }

    @Override
    public java.util.Set<Long> existingLotIds(java.util.Set<Long> ids) { return lots.existingIds(ids); }

    @Override
    public FieldReservationConsolidation consolidate(UUID reservationId, Long lotId, Long agentId, UUID prospectId,
                                                     BigDecimal initialAmount, Instant reservedAt) {
        var outcome = reservationCommandService.handle(new ConsolidateFieldReservationCommand(
                reservationId, lotId, agentId, prospectId, Money.of(initialAmount), reservedAt));
        return new FieldReservationConsolidation(outcome.result().name(), outcome.reservationId(),
                nameOf(outcome.reservationStatus()), outcome.blockedUntil(), nameOf(outcome.conflictReason()),
                nameOf(outcome.originalResult()));
    }

    private static @Nullable String nameOf(@Nullable Enum<?> value) {
        return value == null ? null : value.name();
    }
}
