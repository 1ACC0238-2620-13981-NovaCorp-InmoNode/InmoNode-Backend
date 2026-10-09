package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.notifications.outbox;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.sql.Timestamp;

@Component
@ConditionalOnProperty(name = "financial.notifications.dispatch-job.enabled", havingValue = "true", matchIfMissing = true)
public class FinancialNotificationDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(FinancialNotificationDispatcher.class);
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String from, financeRecipient, legalRecipient;
    private record Notice(long id, String recipient, String subject, String body, int attempts) {}

    public FinancialNotificationDispatcher(JdbcTemplate jdbc, JavaMailSender mail, PlatformTransactionManager manager,
            Clock clock, @Value("${authorization.mail.from}") String from,
            @Value("${financial.notifications.finance-recipient:}") String financeRecipient,
            @Value("${financial.notifications.legal-recipient:}") String legalRecipient) {
        this.jdbc = jdbc; this.mail = mail; this.transactions = new TransactionTemplate(manager); this.clock = clock;
        this.from = from; this.financeRecipient = financeRecipient; this.legalRecipient = legalRecipient;
    }

    @Async("notificationExecutor")
    @Scheduled(fixedDelayString = "${financial.notifications.dispatch-interval:PT1M}")
    public void dispatch() {
        try {
            var ids = jdbc.queryForList("""
                    SELECT id FROM financial_document_control.notification_outbox
                    WHERE delivered_at IS NULL AND attempts < 10 AND next_attempt_at <= ?
                    ORDER BY id LIMIT 25
                    """, Long.class, Timestamp.from(clock.instant()));
            for (var id : ids) transactions.executeWithoutResult(status -> deliver(id));
        } catch (RuntimeException e) { LOG.error("Could not dispatch financial notifications ({})", e.getClass().getSimpleName()); }
    }

    private void deliver(long id) {
        var notices = jdbc.query("""
                SELECT id, recipient, subject, body, attempts FROM financial_document_control.notification_outbox
                WHERE id = ? AND delivered_at IS NULL AND attempts < 10 AND next_attempt_at <= ? FOR UPDATE SKIP LOCKED
                """, (row, number) -> new Notice(row.getLong("id"), row.getString("recipient"), row.getString("subject"),
                row.getString("body"), row.getInt("attempts")), id, Timestamp.from(clock.instant()));
        if (notices.isEmpty()) return;
        var notice = notices.getFirst();
        var recipient = switch (notice.recipient()) {
            case "@finance" -> financeRecipient;
            case "@legal" -> legalRecipient;
            default -> notice.recipient();
        };
        if (recipient.isBlank()) {
            // Configuration can be provided later; keep the durable notice without exhausting retries.
            jdbc.update("UPDATE financial_document_control.notification_outbox SET next_attempt_at = ? WHERE id = ?",
                    Timestamp.from(clock.instant().plusSeconds(3600)), id);
            LOG.warn("Financial notification {} awaits its recipient configuration", id);
            return;
        }
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(recipient); message.setSubject(notice.subject()); message.setText(notice.body());
        try {
            mail.send(message);
            jdbc.update("UPDATE financial_document_control.notification_outbox SET delivered_at = ?, last_error = NULL WHERE id = ?",
                    Timestamp.from(clock.instant()), id);
        } catch (MailException failure) {
            var attempts = notice.attempts() + 1;
            var delay = Math.min(3600L, 30L << attempts);
            jdbc.update("""
                    UPDATE financial_document_control.notification_outbox
                    SET attempts = ?, next_attempt_at = ?, last_error = 'SMTP_DELIVERY_FAILED' WHERE id = ?
                    """, attempts, Timestamp.from(clock.instant().plusSeconds(delay)), id);
            if (attempts >= 10) LOG.error("Financial notification {} exhausted its SMTP retries; back-office review required", id);
        }
    }
}
