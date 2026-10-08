package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterInstallmentPaymentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.AccountStatementView;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A payment locks the lot first, as every other decision about it: two payments of the same statement are recorded
 * one after the other, so the one that pays it off always sees the others and sells the lot.
 */
@Service
public class AccountStatementCommandServiceImpl implements AccountStatementCommandService {

    private final AccountStatementRepository accountStatementRepository;
    private final LotRepository lotRepository;
    private final Clock clock;

    public AccountStatementCommandServiceImpl(AccountStatementRepository accountStatementRepository,
                                              LotRepository lotRepository, Clock clock) {
        this.accountStatementRepository = accountStatementRepository;
        this.lotRepository = lotRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result<AccountStatementView, ApplicationError> handle(RegisterInstallmentPaymentCommand command) {
        var statementId = command.accountStatementId();
        var lotId = accountStatementRepository.findById(statementId).map(AccountStatement::getLotId).orElse(null);
        if (lotId == null) {
            return Result.failure(ApplicationError.notFound("account_statement", String.valueOf(statementId)));
        }
        var lot = lotRepository.findByIdForUpdate(lotId).orElseThrow();
        // Read under the lock: another payment may have changed it.
        var statement = accountStatementRepository.findById(statementId).orElseThrow();
        var number = command.installmentNumber();
        var installment = statement.findInstallment(number).orElse(null);
        if (installment == null) {
            return Result.failure(ApplicationError.notFound("installment",
                    "%d of account statement %d".formatted(number, statementId)));
        }
        if (installment.isPaid()) {
            return Result.failure(ApplicationError.conflict("installment",
                    "installment %d was already paid on %s".formatted(number, installment.getPaidAt())));
        }
        if (!installment.isSettledBy(command.amount())) {
            return Result.failure(ApplicationError.businessRuleViolation("installment-payment",
                    "installment %d is settled with exactly %s %s, its amount plus its late fee"
                            .formatted(number, installment.amountDue(), statement.getCurrency())));
        }
        // PostgreSQL keeps microseconds: the dates answered now must equal the ones read back later.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var paidAt = command.paidAt() == null ? now : command.paidAt().truncatedTo(ChronoUnit.MICROS);
        if (paidAt.isAfter(now)) {
            return Result.failure(ApplicationError.validationError("paidAt", "the payment cannot be in the future"));
        }
        statement.registerInstallmentPayment(number, command.amount(), paidAt);
        var saved = accountStatementRepository.save(statement);
        if (saved.isFullyPaid()) {
            if (!lot.markSold(saved.getReservationId())) {
                throw new IllegalStateException("lot %d is not reserved for reservation %s"
                        .formatted(lotId, saved.getTransactionId()));
            }
            lotRepository.save(lot);
        }
        var today = LocalDate.ofInstant(now, AccountStatement.SALES_ZONE);
        return Result.success(new AccountStatementView(saved, saved.isDueSoon(today)));
    }
}
