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
        if (!projectRepository.existsById(projectId)) {
            return Result.failure(ApplicationError.notFound("project", String.valueOf(projectId)));
        }
        var usedCodes = new HashSet<>(lotRepository.findCodesByProjectId(projectId));
        var accepted = new ArrayList<Lot>();
        var rejected = new ArrayList<RejectedLot>();
        for (int index = 0; index < command.lots().size(); index++) {
            var data = command.lots().get(index);
            try {
                var lot = Lot.register(projectId, data.code(), new LotDimensions(data.area(), data.front(), data.depth()),
                        price(data.price()), LotBoundary.fromPolygonRings(data.polygon()));
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

    private static Money price(@Nullable BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        return Money.of(amount);
    }
}
