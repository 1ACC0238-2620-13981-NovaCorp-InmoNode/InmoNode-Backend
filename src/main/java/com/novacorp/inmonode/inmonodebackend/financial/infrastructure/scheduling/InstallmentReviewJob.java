package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.scheduling;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReviewInstallmentsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * US-24: once a day, in the morning in Lima, the installments of the day are reviewed: overdue ones get their late
 * fee and a notice, and the ones due within 5 days a reminder. A day the job does not run is caught up the next one,
 * since every pending notice is still pending.
 */
@Component
@ConditionalOnProperty(name = "financial.installments.review-job.enabled", havingValue = "true", matchIfMissing = true)
public class InstallmentReviewJob {

    private static final Logger LOG = LoggerFactory.getLogger(InstallmentReviewJob.class);

    private final AccountStatementCommandService accountStatementCommandService;
    private final Clock clock;

    public InstallmentReviewJob(AccountStatementCommandService accountStatementCommandService, Clock clock) {
        this.accountStatementCommandService = accountStatementCommandService;
        this.clock = clock;
    }

    @Scheduled(cron = "${financial.installments.reminder-cron:0 0 8 * * *}", zone = "America/Lima")
    public void notifyUpcomingInstallments() {
        var today = LocalDate.now(clock.withZone(AccountStatement.SALES_ZONE));
        accountStatementCommandService.handle(new com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.NotifyUpcomingInstallmentsCommand(today));
    }

    @Scheduled(cron = "${financial.installments.overdue-cron:0 5 8 * * *}", zone = "America/Lima")
    public void markOverdueInstallments() {
        var today = LocalDate.now(clock.withZone(AccountStatement.SALES_ZONE));
        accountStatementCommandService.handle(new com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.MarkOverdueInstallmentsCommand(today));
    }

    /** Retained for explicit administrative reviews; not scheduled. */
    public void reviewInstallments() {
        var today = LocalDate.now(clock.withZone(AccountStatement.SALES_ZONE));
        var summary = accountStatementCommandService.handle(new ReviewInstallmentsCommand(today));
        if (summary.reviewedStatements() > 0) {
            LOG.info("Reviewed the installments of {} account statement(s) for {}: {} overdue, {} overdue notice(s), "
                            + "{} reminder(s)", summary.reviewedStatements(), today, summary.overdueInstallments(),
                    summary.overdueNotices(), summary.reminders());
        }
    }
}
