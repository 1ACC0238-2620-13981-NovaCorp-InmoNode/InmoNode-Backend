package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotOffer;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.LotBlockOutcome;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.LotSnapshot;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Anti-corruption layer towards Control Financiero y Documental ("LotAvailabilityService" in 2.6.3), the only
 * authority on lots: this context is a Conformist of its Open Host Service and turns its answers into its own values.
 */
@Service
public class ExternalLotAvailabilityService {

    private final LotAvailabilityFacade lotAvailabilityFacade;

    public ExternalLotAvailabilityService(LotAvailabilityFacade lotAvailabilityFacade) {
        this.lotAvailabilityFacade = lotAvailabilityFacade;
    }

    /** The lot with its project's rules; empty when it does not exist or its project is not published. */
    public Optional<LotSnapshot> getLotSnapshot(Long lotId) {
        return lotAvailabilityFacade.findLotOffer(lotId).map(ExternalLotAvailabilityService::toSnapshot);
    }

    /**
     * Asks for the lot to be held for the buyer's request. Joins the caller's transaction, so the block and the
     * request are stored together; a rejection by concurrency is passed on, never retried.
     */
    public LotBlockOutcome blockLot(UUID transactionId, Long lotId, Long buyerId, Money initialAmount,
                                    Instant requestedAt) {
        var block = lotAvailabilityFacade.blockLot(transactionId, lotId, buyerId, initialAmount.amount(),
                initialAmount.currency(), requestedAt);
        var result = switch (block.result()) {
            case "BLOCKED" -> LotBlockOutcome.Result.BLOCKED;
            case "LOT_UNAVAILABLE" -> LotBlockOutcome.Result.UNAVAILABLE;
            case "LOT_NOT_FOUND" -> LotBlockOutcome.Result.NOT_FOUND;
            default -> throw new IllegalStateException("unknown lot block result " + block.result());
        };
        return new LotBlockOutcome(result, block.blockedUntil());
    }

    private static LotSnapshot toSnapshot(LotOffer offer) {
        return new LotSnapshot(offer.lotId(), offer.projectId(), offer.lotCode(), offer.area(),
                new Money(offer.price(), offer.currency()), offer.available(),
                new FinancingRules(offer.minDownPaymentPercentage(), offer.annualInterestRate(),
                        offer.maxTermMonths()));
    }
}
