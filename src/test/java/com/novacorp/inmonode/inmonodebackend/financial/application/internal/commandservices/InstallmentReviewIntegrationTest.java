package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.PaymentNotice;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.PaymentNotificationSender;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReviewInstallmentsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingPlan;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentReviewSummary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementCommandService;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The daily review of the installments (US-24) against a real PostgreSQL, called directly with the day it reviews.
 * The emails are replaced by a mock. Every test opens its statements years before any other test, on days of their
 * own, so the reviews of one test never reach the installments another one checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class InstallmentReviewIntegrationTest {

    private static final BigDecimal LATE_FEE_RATE = new BigDecimal("1.5");

    @MockitoBean
    private PaymentNotificationSender paymentNotificationSender;

    @Autowired
    private AccountStatementCommandService accountStatementCommandService;

    @Autowired
    private AccountStatementRepository accountStatementRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void notificationsAreDelivered() {
        when(paymentNotificationSender.remindUpcoming(any())).thenReturn(true);
        when(paymentNotificationSender.notifyOverdue(any())).thenReturn(true);
    }

    @Test
    void anInstallmentDueWithinFiveDaysIsRemindedOnce() {
        var statement = statementOpenedOn(LocalDate.parse("2019-01-15"));
        var transactionId = statement.getTransactionId();

        review("2019-02-09");
        verify(paymentNotificationSender, never()).remindUpcoming(argThat(about(transactionId, 1)));

        var summary = review("2019-02-10");
        assertTrue(summary.reminders() >= 1);
        verify(paymentNotificationSender).remindUpcoming(argThat(notice -> notice.transactionId().equals(transactionId)
                && notice.installmentNumber() == 1
                && notice.termMonths() == 12
                && notice.dueDate().equals(LocalDate.parse("2019-02-15"))
                && notice.amountDue().equals(new BigDecimal("3198.56"))
                && notice.penalty().equals(new BigDecimal("0.00"))
                && notice.currency().equals("PEN")
                && notice.lotCode().equals("R-01")
                && notice.projectName().equals("Recordatorios")
                && notice.email().equals(emailOf(statement))));
        var first = installment(statement, 1);
        assertNotNull(first.getReminderSentAt());
        assertEquals(InstallmentStatus.PENDING, first.getStatus());

        review("2019-02-12");
        review("2019-02-15");
        verify(paymentNotificationSender, times(1)).remindUpcoming(argThat(about(transactionId, 1)));
        verify(paymentNotificationSender, never()).notifyOverdue(argThat(about(transactionId, 1)));
    }

    @Test
    void anOverdueInstallmentGetsItsLateFeeAndOneNoticeAndIsPaidWithIt() throws Exception {
        var statement = statementOpenedOn(LocalDate.parse("2018-01-15"));
        var transactionId = statement.getTransactionId();

        var summary = review("2018-02-16");

        assertTrue(summary.overdueInstallments() >= 1);
        var first = installment(statement, 1);
        assertEquals(InstallmentStatus.OVERDUE, first.getStatus());
        assertEquals(new BigDecimal("47.98"), first.getPenalty());
        assertNotNull(first.getOverdueNotifiedAt());
        assertNull(first.getReminderSentAt(), "the job was off: it went straight to overdue");
        verify(paymentNotificationSender).notifyOverdue(argThat(notice -> notice.transactionId().equals(transactionId)
                && notice.installmentNumber() == 1
                && notice.amount().equals(new BigDecimal("3198.56"))
                && notice.penalty().equals(new BigDecimal("47.98"))
                && notice.amountDue().equals(new BigDecimal("3246.54"))));

        review("2018-02-17");
        verify(paymentNotificationSender, times(1)).notifyOverdue(argThat(about(transactionId, 1)));
        assertEquals(new BigDecimal("47.98"), installment(statement, 1).getPenalty(), "the fee is charged once");

        pay(statement, 1, "3198.56")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details").value(containsString("3246.54 PEN")));
        pay(statement, 1, "3246.54")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[0].status").value("PAID"))
                .andExpect(jsonPath("$.installments[0].penalty").value(47.98))
                .andExpect(jsonPath("$.installments[0].paidAmount").value(3246.54))
                .andExpect(jsonPath("$.paidAmount").value(12246.54));
    }

    @Test
    void aNoticeThatCouldNotBeDeliveredIsTriedAgainInTheNextReview() {
        var statement = statementOpenedOn(LocalDate.parse("2017-01-15"));
        var transactionId = statement.getTransactionId();
        when(paymentNotificationSender.notifyOverdue(argThat(about(transactionId, 1)))).thenReturn(false, true);

        review("2017-02-16");
        var first = installment(statement, 1);
        assertEquals(InstallmentStatus.OVERDUE, first.getStatus(), "the fee does not wait for the email");
        assertNull(first.getOverdueNotifiedAt());

        review("2017-02-17");
        assertNotNull(installment(statement, 1).getOverdueNotifiedAt());
        verify(paymentNotificationSender, times(2)).notifyOverdue(argThat(about(transactionId, 1)));
        assertEquals(new BigDecimal("47.98"), installment(statement, 1).getPenalty());
    }

    @Test
    void aBuyerWithoutEmailIsNotNotifiedButTheFeeIsCharged() {
        var statement = statementOpenedOn(LocalDate.parse("2016-01-15"), 999_999L);
        var transactionId = statement.getTransactionId();

        review("2016-02-16");

        assertEquals(InstallmentStatus.OVERDUE, installment(statement, 1).getStatus());
        assertNull(installment(statement, 1).getOverdueNotifiedAt());
        verify(paymentNotificationSender, never()).notifyOverdue(argThat(about(transactionId, 1)));
    }

    private InstallmentReviewSummary review(String asOfDate) {
        return accountStatementCommandService.handle(new ReviewInstallmentsCommand(LocalDate.parse(asOfDate)));
    }

    private static ArgumentMatcher<PaymentNotice> about(UUID transactionId, int number) {
        return notice -> notice != null && notice.transactionId().equals(transactionId)
                && notice.installmentNumber() == number;
    }

    private Installment installment(AccountStatement statement, int number) {
        return accountStatementRepository.findById(statement.getId()).orElseThrow()
                .findInstallment(number).orElseThrow();
    }

    private ResultActions pay(AccountStatement statement, int number, String amount) throws Exception {
        var admin = User.restore(77L, "finance77@mail.com", "hash", Role.FINANCE_ADMIN, UserStatus.ACTIVE, null,
                null, 0, null);
        return mockMvc.perform(post("/api/v1/account-statements/{id}/installments/{number}/payment",
                        statement.getId(), number)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.generateToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": %s}".formatted(amount)));
    }

    private String emailOf(AccountStatement statement) {
        return userRepository.findById(statement.getBuyerId()).orElseThrow().getEmail();
    }

    /** A buyer with an account, 9 000 down on 45 000 in 12 months at 12 %; agreed at 10:00 in Lima that day. */
    private AccountStatement statementOpenedOn(LocalDate day) {
        var buyer = User.register("buyer-" + UUID.randomUUID() + "@mail.com", "hash", Role.BUYER, Instant.now());
        buyer.clearDomainEvents();
        return statementOpenedOn(day, userRepository.save(buyer).getId());
    }

    private AccountStatement statementOpenedOn(LocalDate day, Long buyerId) {
        var agreedAt = day.atTime(LocalTime.of(10, 0)).atZone(AccountStatement.SALES_ZONE).toInstant();
        var lot = lot();
        var transactionId = UUID.randomUUID();
        var reservation = reservationRepository.save(Reservation.restore(null, lot.getId(), ReservationChannel.WEB,
                buyerId, null, transactionId, Money.of(new BigDecimal("9000")), agreedAt, ReservationStatus.VERIFIED,
                List.of(), agreedAt, new FinancingPlan(Money.of(new BigDecimal("45000")), 12, new BigDecimal("12"))));
        var contract = contractRepository.save(Contract.restore(null, reservation.getId(), transactionId, buyerId,
                lot.getId(), UUID.randomUUID(), "contracts/%s/contract.pdf".formatted(transactionId), 3000,
                ContractStatus.ISSUED, agreedAt, 77L, agreedAt));
        return accountStatementRepository.save(AccountStatement.open(contract, reservation, agreedAt));
    }

    /** A lot of its own published project, with a late fee of 1.5 %. */
    private Lot lot() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, LATE_FEE_RATE);
        var project = Project.create("Recordatorios", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "R-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }
}
