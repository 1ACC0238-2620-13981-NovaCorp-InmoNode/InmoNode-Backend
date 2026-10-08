package com.novacorp.inmonode.inmonodebackend.financial.application.acl;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestWebReservationCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPublishedLotQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PublishedLot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ProjectQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotBlock;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotOffer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class LotAvailabilityFacadeImpl implements LotAvailabilityFacade {

    private final ProjectQueryService projectQueryService;
    private final ReservationCommandService reservationCommandService;
    private final Clock clock;

    public LotAvailabilityFacadeImpl(ProjectQueryService projectQueryService,
                                     ReservationCommandService reservationCommandService, Clock clock) {
        this.projectQueryService = projectQueryService;
        this.reservationCommandService = reservationCommandService;
        this.clock = clock;
    }

    @Override
    public Optional<LotOffer> findLotOffer(Long lotId) {
        return projectQueryService.handle(new GetPublishedLotQuery(lotId)).map(this::toOffer);
    }

    @Override
    public LotBlock blockLot(UUID transactionId, Long lotId, Long buyerId, BigDecimal initialAmount, String currency,
                             int termMonths, BigDecimal annualInterestRate, Instant requestedAt) {
        var outcome = reservationCommandService.handle(new RequestWebReservationCommand(transactionId, lotId, buyerId,
                new Money(initialAmount, currency), termMonths, annualInterestRate, requestedAt));
        return new LotBlock(outcome.result().name(), outcome.reservationId(), outcome.blockedUntil());
    }

    private LotOffer toOffer(PublishedLot published) {
        var project = published.project();
        var lot = published.lot();
        var rules = project.getFinancingRules();
        return new LotOffer(Objects.requireNonNull(lot.getId()), lot.getProjectId(), project.getName(), lot.getCode(),
                lot.getDimensions().area(), lot.getPrice().amount(), lot.getPrice().currency(),
                lot.isAvailable(clock.instant()), rules.minDownPaymentPercentage(), rules.annualInterestRate(),
                rules.maxTermMonths());
    }
}
