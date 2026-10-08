package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationAccountStatementQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.AccountStatementView;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class AccountStatementQueryServiceImpl implements AccountStatementQueryService {

    private final ReservationRepository reservationRepository;
    private final AccountStatementRepository accountStatementRepository;
    private final ExternalIamService externalIamService;
    private final Clock clock;

    public AccountStatementQueryServiceImpl(ReservationRepository reservationRepository,
                                            AccountStatementRepository accountStatementRepository,
                                            ExternalIamService externalIamService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.accountStatementRepository = accountStatementRepository;
        this.externalIamService = externalIamService;
        this.clock = clock;
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
