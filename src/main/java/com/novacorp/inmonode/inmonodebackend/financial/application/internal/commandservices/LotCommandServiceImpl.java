package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ImportLotsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotImportResult;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotImportResult.RejectedLot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.LotCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterLotCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.PublishLotCommand;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;

@Service
public class LotCommandServiceImpl implements LotCommandService {

    private final ProjectRepository projectRepository;
    private final LotRepository lotRepository;

    public LotCommandServiceImpl(ProjectRepository projectRepository, LotRepository lotRepository) {
        this.projectRepository = projectRepository;
        this.lotRepository = lotRepository;
    }

    @Override
    @Transactional
    public Result<LotImportResult, ApplicationError> handle(ImportLotsCommand command) {
        var projectId = command.projectId();
        var project = projectRepository.findByIdForUpdate(projectId).orElse(null);
        if (project == null) {
            return Result.failure(ApplicationError.notFound("project", String.valueOf(projectId)));
        }
        var stage = project.getStages().getFirst();
        var usedCodes = new HashSet<>(lotRepository.findCodesByProjectId(projectId));
        var accepted = new ArrayList<Lot>();
        var rejected = new ArrayList<RejectedLot>();
        for (int index = 0; index < command.lots().size(); index++) {
            var data = command.lots().get(index);
            try {
                var lot = Lot.register(projectId, data.code(), new LotDimensions(data.area(), data.front(), data.depth()),
                        price(data.price()), LotBoundary.fromPolygonRings(data.polygon()));
                Lot.withStage(lot, stage);
                if (usedCodes.add(lot.getCode())) {
                    accepted.add(lot);
                } else {
                    rejected.add(new RejectedLot(index, data.code(),
                            "the code %s is repeated in the plan or already used in the project".formatted(lot.getCode())));
                }
            } catch (IllegalArgumentException ex) {
                rejected.add(new RejectedLot(index, data.code(), ex.getMessage()));
            }
        }
        lotRepository.saveAll(accepted);
        return Result.success(new LotImportResult(accepted.size(), rejected));
    }

    @Override
    @Transactional
    public Result<Lot, ApplicationError> handle(RegisterLotCommand command) {
        var project = projectRepository.findByIdForUpdate(command.projectId()).orElse(null);
        if (project == null) return Result.failure(ApplicationError.notFound("project", String.valueOf(command.projectId())));
        var stage = project.getStages().stream().filter(name -> name.equalsIgnoreCase(command.stageName().strip()))
                .findFirst().orElse(null);
        if (stage == null) return Result.failure(ApplicationError.validationError("stageName", "stageName does not belong to this project"));
        var code = Lot.normalizeCode(command.code());
        if (lotRepository.findCodesByProjectId(command.projectId()).contains(code)) {
            return Result.failure(ApplicationError.conflict("lot", "the code is already used in this project"));
        }
        return Result.success(lotRepository.save(Lot.registerDraft(command.projectId(), stage, code,
                command.dimensions(), command.price(), command.boundary())));
    }

    @Override
    @Transactional
    public Result<Lot, ApplicationError> handle(PublishLotCommand command) {
        var lot = lotRepository.findByIdForUpdate(command.lotId()).orElse(null);
        if (lot == null) return Result.failure(ApplicationError.notFound("lot", String.valueOf(command.lotId())));
        var project = projectRepository.findById(lot.getProjectId()).orElseThrow();
        if (!project.isPublished()) return Result.failure(ApplicationError.businessRuleViolation("lot-publication", "Publish the project before publishing its lots"));
        if (!project.getStages().contains(lot.getStageName())) return Result.failure(ApplicationError.validationError("stageName", "stageName does not belong to this project"));
        return Result.success(lot.publish() ? lotRepository.save(lot) : lot);
    }

    private static Money price(@Nullable BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        return Money.of(amount);
    }
}
