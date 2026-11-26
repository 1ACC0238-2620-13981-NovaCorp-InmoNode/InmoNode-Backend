package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.scheduling;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.MarkOverdueInstallmentsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.NotifyUpcomingInstallmentsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementCommandService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** US-24 on a host that sleeps: the reviews whose time already passed are run when the backend starts. */
class InstallmentReviewJobTest {

    private static final String REMINDER_CRON = "0 0 8 * * *";
    private static final String OVERDUE_CRON = "0 5 8 * * *";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final AccountStatementCommandService statements = mock(AccountStatementCommandService.class);

    private InstallmentReviewJob startedAt(String instant, String reminderCron, String overdueCron) {
        return new InstallmentReviewJob(statements, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                reminderCron, overdueCron);
    }

    @Test
    void startingAfterBothReviewsRunsBothForTheDayInLima() {
        // 09:30 in Lima.
        startedAt("2026-11-26T14:30:00Z", REMINDER_CRON, OVERDUE_CRON).catchUpAfterStart();

        var today = LocalDate.of(2026, 11, 26);
        verify(statements).handle(new NotifyUpcomingInstallmentsCommand(today));
        verify(statements).handle(new MarkOverdueInstallmentsCommand(today));
    }

    @Test
    void startingBetweenTheReviewsRunsOnlyTheOneWhoseTimePassed() {
        // 08:02 in Lima: the reminders were due at 08:00; the overdue review still runs on its own at 08:05.
        startedAt("2026-11-26T13:02:00Z", REMINDER_CRON, OVERDUE_CRON).catchUpAfterStart();

        verify(statements).handle(new NotifyUpcomingInstallmentsCommand(LocalDate.of(2026, 11, 26)));
        verify(statements, never()).handle(any(MarkOverdueInstallmentsCommand.class));
    }

    @Test
    void startingBeforeTheMorningRunsNothing() {
        // 07:30 in Lima: the crons will run at their time.
        startedAt("2026-11-26T12:30:00Z", REMINDER_CRON, OVERDUE_CRON).catchUpAfterStart();

        verifyNoInteractions(statements);
    }

    @Test
    void theDayIsTheOneInLimaNotInUtc() {
        // 01:00 UTC on the 27th is still 20:00 on the 26th in Lima.
        startedAt("2026-11-27T01:00:00Z", REMINDER_CRON, OVERDUE_CRON).catchUpAfterStart();

        verify(statements).handle(new NotifyUpcomingInstallmentsCommand(LocalDate.of(2026, 11, 26)));
        verify(statements).handle(new MarkOverdueInstallmentsCommand(LocalDate.of(2026, 11, 26)));
    }

    @Test
    void aDisabledCronIsNeverCaughtUp() {
        startedAt("2026-11-26T14:30:00Z", "-", OVERDUE_CRON).catchUpAfterStart();

        verify(statements, never()).handle(any(NotifyUpcomingInstallmentsCommand.class));
        verify(statements).handle(new MarkOverdueInstallmentsCommand(LocalDate.of(2026, 11, 26)));
    }

    @Test
    void aFailingReviewDoesNotStopTheOtherOrTheStart() {
        when(statements.handle(any(NotifyUpcomingInstallmentsCommand.class)))
                .thenThrow(new IllegalStateException("database down"));

        var job = startedAt("2026-11-26T14:30:00Z", REMINDER_CRON, OVERDUE_CRON);

        assertDoesNotThrow(job::catchUpAfterStart);
        verify(statements).handle(new MarkOverdueInstallmentsCommand(LocalDate.of(2026, 11, 26)));
    }

    @Test
    void aCronWithoutARunTodayHasNotPassed() {
        var mondayMorning = ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, LIMA);
        var sundayMorning = ZonedDateTime.of(2026, 11, 22, 9, 0, 0, 0, LIMA);

        assertTrue(InstallmentReviewJob.passedToday("0 0 8 * * MON", mondayMorning));
        assertFalse(InstallmentReviewJob.passedToday("0 0 8 * * MON", sundayMorning));
        assertTrue(InstallmentReviewJob.passedToday("0 0 8 * * *", mondayMorning.withHour(8)));
        assertFalse(InstallmentReviewJob.passedToday("0 0 8 * * *", mondayMorning.withHour(7).withMinute(59)));
    }
}
