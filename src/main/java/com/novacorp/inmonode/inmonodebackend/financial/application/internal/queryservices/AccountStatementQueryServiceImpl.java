package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetBuyerAccountStatementsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationAccountStatementQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.AccountStatementView;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.BuyerAccountStatements;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class AccountStatementQueryServiceImpl implements AccountStatementQueryService {

    private final ReservationRepository reservationRepository;
    private final AccountStatementRepository accountStatementRepository;
    private final LotRepository lotRepository;
    private final ProjectRepository projectRepository;
    private final ExternalIamService externalIamService;
    private final Clock clock;

    public AccountStatementQueryServiceImpl(ReservationRepository reservationRepository,
                                            AccountStatementRepository accountStatementRepository,
                                            LotRepository lotRepository, ProjectRepository projectRepository,
                                            ExternalIamService externalIamService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.accountStatementRepository = accountStatementRepository;
        this.lotRepository = lotRepository;
        this.projectRepository = projectRepository;
        this.externalIamService = externalIamService;
        this.clock = clock;
    }

    @Override
    public Result<BuyerAccountStatements, ApplicationError> handle(GetBuyerAccountStatementsQuery query) {
        var buyerId = externalIamService.currentUserId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        var today = LocalDate.now(clock.withZone(AccountStatement.SALES_ZONE));
        var projects = new HashMap<Long, Project>();
        var entries = accountStatementRepository.findByBuyerId(buyerId).stream()
                .map(statement -> {
                    var lot = lotRepository.findById(statement.getLotId()).orElseThrow();
                    var project = projects.computeIfAbsent(lot.getProjectId(),
                            projectId -> projectRepository.findById(projectId).orElseThrow());
                    return new BuyerAccountStatements.Entry(statement, lot.getProjectId(), project.getName(),
                            lot.getCode(), statement.isDueSoon(today));
                })
                .toList();
        return Result.success(new BuyerAccountStatements(entries));
    }

    @Override
    public Result<AccountStatementView, ApplicationError> handle(GetReservationAccountStatementQuery query) {
        var buyerId = externalIamService.currentUserId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        var reservation = reservationRepository.findBySourceEventId(query.transactionId())
                .filter(found -> found.getRequesterId().equals(buyerId))
                .orElse(null);
        if (reservation == null) {
            return Result.failure(ApplicationError.notFound("reservation", query.transactionId().toString()));
        }
        var today = LocalDate.now(clock.withZone(AccountStatement.SALES_ZONE));
        return accountStatementRepository.findByReservationId(Objects.requireNonNull(reservation.getId()))
                .map(statement -> Result.<AccountStatementView, ApplicationError>success(
                        new AccountStatementView(statement, statement.isDueSoon(today))))
                .orElseGet(() -> Result.failure(ApplicationError.notFound("account_statement",
                        query.transactionId().toString())));
    }
}
