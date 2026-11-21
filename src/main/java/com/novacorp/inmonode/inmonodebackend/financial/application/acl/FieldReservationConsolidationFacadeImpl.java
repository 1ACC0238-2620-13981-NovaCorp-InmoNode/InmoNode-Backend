package com.novacorp.inmonode.inmonodebackend.financial.application.acl;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ConsolidateFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FieldReservationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.NegotiationTerms;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidation;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public Map<Long, BigDecimal> lotPrices(Set<Long> ids) {
        return lots.pricesOf(ids).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, price -> price.getValue().amount()));
    }

    @Override
    public FieldReservationConsolidation consolidate(UUID reservationId, Long lotId, Long agentId, UUID prospectId,
                                                     BigDecimal initialAmount, Instant reservedAt,
                                                     @Nullable BigDecimal discountAmount, @Nullable String bonus,
                                                     @Nullable String negotiationNotes) {
        var outcome = reservationCommandService.handle(new ConsolidateFieldReservationCommand(
                reservationId, lotId, agentId, prospectId, Money.of(initialAmount), reservedAt,
                new NegotiationTerms(discountAmount, bonus, negotiationNotes)));
        return new FieldReservationConsolidation(outcome.result().name(), outcome.reservationId(),
                nameOf(outcome.reservationStatus()), outcome.blockedUntil(), nameOf(outcome.conflictReason()),
                nameOf(outcome.originalResult()));
    }

    private static @Nullable String nameOf(@Nullable Enum<?> value) {
        return value == null ? null : value.name();
    }
}
