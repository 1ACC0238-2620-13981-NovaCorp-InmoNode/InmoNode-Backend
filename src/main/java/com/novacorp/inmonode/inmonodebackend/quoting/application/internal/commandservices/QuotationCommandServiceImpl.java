package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl.ExternalLotAvailabilityService;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands.SimulateFinancingCommand;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.InitialPayment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.repositories.QuotationRepository;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.QuotationCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Service
public class QuotationCommandServiceImpl implements QuotationCommandService {

    private final QuotationRepository quotationRepository;
    private final ExternalLotAvailabilityService externalLotAvailabilityService;
    private final ExternalIamService externalIamService;
    private final Clock clock;
    private final Duration validity;

    /**
     * @param validity how long a quotation can back a separation request
     */
    public QuotationCommandServiceImpl(QuotationRepository quotationRepository,
                                       ExternalLotAvailabilityService externalLotAvailabilityService,
                                       ExternalIamService externalIamService, Clock clock,
                                       @Value("${quoting.quotation.validity:P7D}") Duration validity) {
        this.quotationRepository = quotationRepository;
        this.externalLotAvailabilityService = externalLotAvailabilityService;
        this.externalIamService = externalIamService;
        this.clock = clock;
        this.validity = validity;
    }

    @Override
    @Transactional
    public Result<Quotation, ApplicationError> handle(SimulateFinancingCommand command) {
        var buyerId = externalIamService.currentBuyerId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        var lot = externalLotAvailabilityService.getLotSnapshot(command.lotId()).orElse(null);
        if (lot == null) {
            return Result.failure(ApplicationError.notFound("lot", String.valueOf(command.lotId())));
        }
        if (!lot.availableAtQueryTime()) {
            return Result.failure(ApplicationError.conflict("lot",
                    "lot %s is not available; choose another lot".formatted(lot.code())));
        }
        var initialPayment = new InitialPayment(new Money(command.initialPayment(), lot.price().currency()));
        // PostgreSQL keeps microseconds: the dates answered now must equal the ones read back later.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var quotation = Quotation.simulate(buyerId, lot, initialPayment, command.termMonths(), now, validity);
        return Result.success(quotationRepository.save(quotation));
    }
}
