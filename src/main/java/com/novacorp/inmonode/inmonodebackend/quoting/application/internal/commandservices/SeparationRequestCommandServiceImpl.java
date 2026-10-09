package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl.ExternalLotAvailabilityService;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.SeparationRequest;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands.RequestLotSeparationCommand;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.repositories.QuotationRepository;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.repositories.SeparationRequestRepository;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.SeparationRequestCommandService;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.events.SeparationRequestRegisteredEvent;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class SeparationRequestCommandServiceImpl implements SeparationRequestCommandService {

    private final SeparationRequestRepository separationRequestRepository;
    private final QuotationRepository quotationRepository;
    private final ExternalLotAvailabilityService externalLotAvailabilityService;
    private final ExternalIamService externalIamService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SeparationRequestCommandServiceImpl(SeparationRequestRepository separationRequestRepository,
                                               QuotationRepository quotationRepository,
                                               ExternalLotAvailabilityService externalLotAvailabilityService,
                                               ExternalIamService externalIamService,
                                               ApplicationEventPublisher eventPublisher, Clock clock) {
        this.separationRequestRepository = separationRequestRepository;
        this.quotationRepository = quotationRepository;
        this.externalLotAvailabilityService = externalLotAvailabilityService;
        this.externalIamService = externalIamService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * One transaction with the lot block of financial and the "Solicitud de separación registrada" listeners: the
     * block, the request and what the listeners store are kept together, or none of it is. A rejection by concurrency
     * is not an error of the transaction, so it is stored too.
     */
    @Override
    @Transactional
    public Result<SeparationRequest, ApplicationError> handle(RequestLotSeparationCommand command) {
        var buyerId = externalIamService.currentBuyerId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        var quotation = quotationRepository.findById(command.quotationId())
                .filter(found -> found.belongsTo(buyerId) && found.getLotId().equals(command.lotId()))
                .orElse(null);
        if (quotation == null) {
            return Result.failure(ApplicationError.notFound("quotation", String.valueOf(command.quotationId())));
        }
        // PostgreSQL keeps microseconds: the dates answered now must equal the ones read back later.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!quotation.isValid(now)) {
            return Result.failure(ApplicationError.businessRuleViolation("quotation-validity",
                    "quotation %d expired at %s; simulate the financing again"
                            .formatted(quotation.getId(), quotation.getValidUntil())));
        }
        var active = separationRequestRepository.findLatestBlocked(buyerId, command.lotId())
                .filter(request -> request.isActive(now));
        if (active.isPresent()) {
            return Result.success(active.get());
        }
        return block(quotation, now);
    }

    private Result<SeparationRequest, ApplicationError> block(Quotation quotation, Instant now) {
        var transactionId = UUID.randomUUID();
        var outcome = externalLotAvailabilityService.blockLot(transactionId, quotation.getLotId(),
                quotation.getBuyerId(), quotation.getInitialPayment(), quotation.getTermMonths(),
                quotation.getAnnualInterestRate(), now, quotation.getLotPrice(), quotation.getId());
        return switch (outcome.result()) {
            case NOT_FOUND -> Result.failure(ApplicationError.notFound("lot", String.valueOf(quotation.getLotId())));
            case UNAVAILABLE -> {
                separationRequestRepository.save(SeparationRequest.rejectedUnavailable(transactionId, quotation, now));
                yield Result.failure(ApplicationError.conflict("lot",
                        "lot %s was taken by another operation; choose another lot".formatted(quotation.getLotCode())));
            }
            case BLOCKED -> {
                var request = separationRequestRepository.save(SeparationRequest.blocked(transactionId, quotation, now,
                        Objects.requireNonNull(outcome.blockedUntil())));
                eventPublisher.publishEvent(toRegisteredEvent(request));
                yield Result.success(request);
            }
        };
    }

    private static SeparationRequestRegisteredEvent toRegisteredEvent(SeparationRequest request) {
        return new SeparationRequestRegisteredEvent(request.getTransactionId(), request.getLotId(),
                request.getBuyerId(), request.getInitialAmount().amount(), request.getInitialAmount().currency(),
                request.getRequestedAt(), Objects.requireNonNull(request.getLockExpiresAt()));
    }
}
