package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotOffer;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.LotSnapshot;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    private static LotSnapshot toSnapshot(LotOffer offer) {
        return new LotSnapshot(offer.lotId(), offer.projectId(), offer.lotCode(), offer.area(),
                new Money(offer.price(), offer.currency()), offer.available(),
                new FinancingRules(offer.minDownPaymentPercentage(), offer.annualInterestRate(),
                        offer.maxTermMonths()));
    }
}
