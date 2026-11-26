package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.scheduling;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.MarkOverdueInstallmentsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.NotifyUpcomingInstallmentsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReviewInstallmentsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * US-24: once a day, in the morning in Lima, the installments of the day are reviewed: overdue ones get their late
 * fee and a notice, and the ones due within 5 days a reminder. A day the job does not run is caught up the next one,
 * since every pending notice is still pending.
 *
 * <p>A host that sleeps while idle, such as Render's free instance, runs no job at the time it is asleep. So, when the
 * backend starts after the time of a review on that day, it runs that review then. Running a review again changes
 * nothing: a reminder or an overdue notice is sent once, and the late fee is added once.
 */
@Component
@ConditionalOnProperty(name = "financial.installments.review-job.enabled", havingValue = "true", matchIfMissing = true)
public class InstallmentReviewJob {

    private static final Logger LOG = LoggerFactory.getLogger(InstallmentReviewJob.class);
    /** Spring's value for a cron that never runs. */
    private static final String DISABLED = "-";

    private final AccountStatementCommandService accountStatementCommandService;
    private final Clock clock;
    private final String reminderCron;
    private final String overdueCron;

    public InstallmentReviewJob(AccountStatementCommandService accountStatementCommandService, Clock clock,
                                @Value("${financial.installments.reminder-cron:0 0 8 * * *}") String reminderCron,
                                @Value("${financial.installments.overdue-cron:0 5 8 * * *}") String overdueCron) {
        this.accountStatementCommandService = accountStatementCommandService;
        this.clock = clock;
        this.reminderCron = reminderCron;
        this.overdueCron = overdueCron;
    }

    @Scheduled(cron = "${financial.installments.reminder-cron:0 0 8 * * *}", zone = "America/Lima")
    public void notifyUpcomingInstallments() {
        var today = LocalDate.now(clock.withZone(AccountStatement.SALES_ZONE));
        accountStatementCommandService.handle(new NotifyUpcomingInstallmentsCommand(today));
    }

    @Scheduled(cron = "${financial.installments.overdue-cron:0 5 8 * * *}", zone = "America/Lima")
    public void markOverdueInstallments() {
        var today = LocalDate.now(clock.withZone(AccountStatement.SALES_ZONE));
        accountStatementCommandService.handle(new MarkOverdueInstallmentsCommand(today));
    }

    /** Runs the reviews whose time already passed today, in case the backend was asleep or down at that time. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterStart() {
        var now = ZonedDateTime.now(clock.withZone(AccountStatement.SALES_ZONE));
        if (passedToday(reminderCron, now)) {
            catchUp("reminders of upcoming installments", now, this::notifyUpcomingInstallments);
        }
        if (passedToday(overdueCron, now)) {
            catchUp("review of overdue installments", now, this::markOverdueInstallments);
        }
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

    /** Whether the cron had a run today, in the zone of {@code now}, at or before {@code now}. */
    static boolean passedToday(String cron, ZonedDateTime now) {
        if (cron == null || cron.isBlank() || DISABLED.equals(cron.trim())) {
            return false;
        }
        var startOfDay = now.toLocalDate().atStartOfDay(now.getZone());
        var firstRunToday = CronExpression.parse(cron).next(startOfDay.minusNanos(1));
        return firstRunToday != null && firstRunToday.toLocalDate().equals(now.toLocalDate())
                && !firstRunToday.isAfter(now);
    }

    /** A failure is logged and never stops the backend from starting. */
    private void catchUp(String review, ZonedDateTime now, Runnable run) {
        try {
            run.run();
            LOG.info("Started at {} after the time of today's {}; it was run now", now.toLocalTime(), review);
        } catch (RuntimeException failure) {
            LOG.error("Could not run today's {} after starting; the next run will catch it up", review, failure);
        }
    }
}
