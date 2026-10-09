package com.novacorp.inmonode.inmonodebackend.catalog.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.catalog.application.internal.outboundservices.acl.ExternalFinancialService;
import com.novacorp.inmonode.inmonodebackend.catalog.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.aggregates.Prospect;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.RegisterProspectsCommand;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.RegisterProspectsCommand.ProspectData;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.SyncFieldRecordsCommand;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.SyncFieldRecordsCommand.ReservationData;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.valueobjects.FieldSyncResult;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.valueobjects.ReservationSyncOutcome;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.repositories.ProspectRepository;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.services.FieldSyncCommandService;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.services.ProspectCommandService;
import com.novacorp.inmonode.inmonodebackend.catalog.interfaces.events.FieldLotReservedEvent;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validates the entire batch before writing. Availability conflicts are normal per-item outcomes;
 * a technical failure rolls back prospects, reservations and voucher operations together.
 */
@Service
public class FieldSyncCommandServiceImpl implements FieldSyncCommandService {

    private final ProspectCommandService prospectCommandService;
    private final ProspectRepository prospectRepository;
    private final ExternalFinancialService externalFinancialService;
    private final ExternalIamService externalIamService;
    private final ApplicationEventPublisher eventPublisher;

    public FieldSyncCommandServiceImpl(ProspectCommandService prospectCommandService,
                                       ProspectRepository prospectRepository,
                                       ExternalFinancialService externalFinancialService,
                                       ExternalIamService externalIamService,
                                       ApplicationEventPublisher eventPublisher) {
        this.prospectCommandService = prospectCommandService;
        this.prospectRepository = prospectRepository;
        this.externalFinancialService = externalFinancialService;
        this.externalIamService = externalIamService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result<FieldSyncResult, ApplicationError> handle(SyncFieldRecordsCommand command) {
        var agentId = externalIamService.currentAgentId().orElse(null);
        if (agentId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The agent is not authenticated"));
        }
        for (int index = 0; index < command.prospects().size(); index++) {
            var data = command.prospects().get(index);
            try {
                Prospect.register(data.prospectId(), agentId, data.document(), data.fullName(), data.phone(),
                        data.registeredAt() == null ? java.time.Instant.EPOCH : data.registeredAt());
            } catch (IllegalArgumentException invalid) {
                var field = "prospects[%d]".formatted(index);
                return Result.failure(ApplicationError.validationError(field, field + ": " + invalid.getMessage()));
            }
        }
        var incomingIds = command.prospects().stream().map(ProspectData::prospectId).toList();
        var foreignIds = prospectRepository.findByProspectIds(incomingIds).stream()
                .filter(prospect -> !prospect.isRegisteredBy(agentId)).map(Prospect::getProspectId).collect(Collectors.toSet());
        for (int index = 0; index < command.prospects().size(); index++) {
            if (foreignIds.contains(command.prospects().get(index).prospectId())) {
                var field = "prospects[%d].id".formatted(index);
                return Result.failure(ApplicationError.validationError(field, field + ": prospect is not owned by this agent"));
            }
        }
        var unknownProspect = firstUnknownProspect(command, agentId);
        if (unknownProspect.isPresent()) {
            return Result.failure(unknownProspect.get());
        }
        var referencedLots = command.reservations().stream().map(ReservationData::lotId).collect(Collectors.toSet());
        var knownLots = externalFinancialService.existingLotIds(referencedLots);
        for (int index = 0; index < command.reservations().size(); index++) {
            if (!knownLots.contains(command.reservations().get(index).lotId())) {
                var field = "reservations[%d].lotId".formatted(index);
                return Result.failure(ApplicationError.validationError(field, field + ": lot does not exist"));
            }
        }
        var prospectsSynced = prospectCommandService.handle(new RegisterProspectsCommand(agentId, command.prospects()));
        var outcomes = command.reservations().stream()
                .map(reservation -> consolidate(agentId, reservation))
                .toList();
        return Result.success(new FieldSyncResult(prospectsSynced, outcomes));
    }

    /**
     * A reservation that took its lot is announced as "Lote separado"; conflicts never are. A re-send of one that took
     * its lot is announced again (at-least-once): if a listener failed the first time, the device's retry is the only
     * chance to deliver it, so listeners must be idempotent.
     */
    private ReservationSyncOutcome consolidate(Long agentId, ReservationData reservation) {
        var outcome = externalFinancialService.consolidate(agentId, reservation);
        if (tookItsLot(outcome)) {
            eventPublisher.publishEvent(new FieldLotReservedEvent(reservation.reservationId(), agentId,
                    reservation.lotId(), reservation.initialAmount(), reservation.reservedAt(),
                    outcome.blockedUntil()));
        }
        return outcome;
    }

    private static boolean tookItsLot(ReservationSyncOutcome outcome) {
        return outcome.result() == ReservationSyncOutcome.Result.SYNCED
                || (outcome.result() == ReservationSyncOutcome.Result.DUPLICATE
                    && outcome.originalResult() == ReservationSyncOutcome.Result.SYNCED);
    }

    /**
     * US-32, Scenario 3: every reservation must name a prospect of this sync or one the agent synchronized before;
     * otherwise the whole sync is rejected, pointing at the first offending index, before anything is stored.
     */
    private Optional<ApplicationError> firstUnknownProspect(SyncFieldRecordsCommand command, Long agentId) {
        var known = command.prospects().stream().map(ProspectData::prospectId).collect(Collectors.toCollection(HashSet::new));
        var referencedElsewhere = command.reservations().stream()
                .map(ReservationData::prospectId)
                .filter(prospectId -> !known.contains(prospectId))
                .collect(Collectors.toSet());
        prospectRepository.findByProspectIds(referencedElsewhere).stream()
                .filter(prospect -> prospect.isRegisteredBy(agentId))
                .map(Prospect::getProspectId)
                .forEach(known::add);
        for (int index = 0; index < command.reservations().size(); index++) {
            UUID prospectId = command.reservations().get(index).prospectId();
            if (!known.contains(prospectId)) {
                var field = "reservations[%d].prospectId".formatted(index);
                return Optional.of(ApplicationError.validationError(field,
                        "%s: prospect %s is neither in this sync nor registered before by the agent"
                                .formatted(field, prospectId)));
            }
        }
        return Optional.empty();
    }
}
