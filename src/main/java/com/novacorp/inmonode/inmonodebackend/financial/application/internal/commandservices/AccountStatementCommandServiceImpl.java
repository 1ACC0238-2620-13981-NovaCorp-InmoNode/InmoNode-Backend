package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.PaymentNotice;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.PaymentNotificationSender;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterInstallmentPaymentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReviewInstallmentsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.AccountStatementView;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentReviewSummary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A payment and the review lock the lot first, as every other decision about it: two changes of the same statement
 * are made one after the other, so each payment sees the others, and the
 * review never overwrites a payment with a late fee.
 */
@Service
public class AccountStatementCommandServiceImpl implements AccountStatementCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountStatementCommandServiceImpl.class);

    private final AccountStatementRepository accountStatementRepository;
    private final LotRepository lotRepository;
    private final ProjectRepository projectRepository;
    private final ExternalIamService externalIamService;
    private final PaymentNotificationSender paymentNotificationSender;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AccountStatementCommandServiceImpl(AccountStatementRepository accountStatementRepository,
                                              LotRepository lotRepository, ProjectRepository projectRepository,
                                              ExternalIamService externalIamService,
                                              PaymentNotificationSender paymentNotificationSender,
                                              PlatformTransactionManager transactionManager, Clock clock) {
        this.accountStatementRepository = accountStatementRepository;
        this.lotRepository = lotRepository;
        this.projectRepository = projectRepository;
        this.externalIamService = externalIamService;
        this.paymentNotificationSender = paymentNotificationSender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Not transactional itself: each statement is reviewed in its own transaction, so the lock of its lot is held
     * only while it is reviewed and a statement that fails does not undo the others.
     */
    @Override
    public InstallmentReviewSummary handle(ReviewInstallmentsCommand command) {
        return reviewAll(command.asOfDate(), true, true);
    }

    @Override
    public InstallmentReviewSummary handle(com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.NotifyUpcomingInstallmentsCommand command) {
        return reviewAll(command.asOfDate(), true, false);
    }

    @Override
    public InstallmentReviewSummary handle(com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.MarkOverdueInstallmentsCommand command) {
        return reviewAll(command.asOfDate(), false, true);
    }

    private InstallmentReviewSummary reviewAll(LocalDate asOfDate, boolean remind, boolean markOverdue) {
        var summary = InstallmentReviewSummary.NONE;
        for (var statementId : accountStatementRepository.findIdsToReview(asOfDate, AccountStatement.DUE_SOON_DAYS)) {
            try {
                var reviewed = transactionTemplate.execute(status -> review(statementId, asOfDate, remind, markOverdue));
                summary = summary.plus(Objects.requireNonNull(reviewed));
            } catch (RuntimeException ex) {
                LOG.error("Could not review the installments of account statement {}", statementId, ex);
            }
        }
        return summary;
    }

    private InstallmentReviewSummary review(Long statementId, LocalDate asOfDate, boolean remind, boolean markOverdue) {
        var lotId = accountStatementRepository.findById(statementId).orElseThrow().getLotId();
        var lot = lotRepository.findByIdForUpdate(lotId).orElseThrow();
        // Read under the lock: a payment may have changed it.
        var statement = accountStatementRepository.findByIdForUpdate(statementId).orElseThrow();
        var project = projectRepository.findById(lot.getProjectId()).orElseThrow();
        var overdue = markOverdue ? statement.markOverdueInstallments(asOfDate, project.getFinancingRules().lateFeeRate()) : java.util.List.<Installment>of();
        var email = externalIamService.emailOf(statement.getBuyerId()).orElse(null);
        var overdueNotices = 0;
        var reminders = 0;
        if (email == null) {
            LOG.warn("Buyer {} of account statement {} has no email; their notices wait", statement.getBuyerId(),
                    statementId);
        } else {
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            for (var installment : markOverdue ? statement.overdueInstallmentsToNotify() : java.util.List.<Installment>of()) {
                if (paymentNotificationSender.notifyOverdue(notice(email, statement, project, lot, installment))) {
                    statement.recordOverdueNotice(installment.getNumber(), now);
                    overdueNotices++;
                }
            }
            for (var installment : remind ? statement.installmentsToRemind(asOfDate) : java.util.List.<Installment>of()) {
                if (paymentNotificationSender.remindUpcoming(notice(email, statement, project, lot, installment))) {
                    statement.recordReminder(installment.getNumber(), now);
                    reminders++;
                }
            }
        }
        if (!overdue.isEmpty() || overdueNotices > 0 || reminders > 0) {
            accountStatementRepository.save(statement);
        }
        return new InstallmentReviewSummary(1, overdue.size(), overdueNotices, reminders);
    }

    private static PaymentNotice notice(String email, AccountStatement statement, Project project, Lot lot,
                                        Installment installment) {
        return new PaymentNotice(email, statement.getTransactionId(), project.getName(), lot.getCode(),
                installment.getNumber(), statement.getTermMonths(), installment.getDueDate(), installment.getAmount(),
                installment.getPenalty(), installment.amountDue(), statement.getCurrency());
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
        var statement = accountStatementRepository.findByIdForUpdate(statementId).orElseThrow();
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
        // SOLD is a legal-contract transition (US-56), independent of the payment balance.
        var today = LocalDate.ofInstant(now, AccountStatement.SALES_ZONE);
        return Result.success(new AccountStatementView(saved, saved.isDueSoon(today)));
    }
}
