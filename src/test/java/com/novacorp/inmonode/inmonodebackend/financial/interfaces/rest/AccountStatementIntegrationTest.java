package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A buyer gets the account statement of their web separation when the contract is issued (US-23), end to end over
 * HTTP against a real PostgreSQL and a real S3-compatible storage: quote, separate, payment, verification, issuing and
 * agreement. Each test uses its own buyers and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class AccountStatementIntegrationTest {

    private static final AtomicLong BUYERS = new AtomicLong(13_000);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private AccountStatementRepository accountStatementRepository;

    @Autowired
    private ReservationCommandService reservationCommandService;

    @Test
    void issuingOpensTheStatementBeforeAcknowledgmentWithTheQuotedSchedule() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot();
        var quotation = quote(buyer, lot);
        var transactionId = separate(buyer, lot, quotation);
        approve(sendVoucher(transactionId));
        var contractId = issue(buyer, transactionId);

        statementOf(buyer, transactionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(0.00));

        acknowledge(buyer, contractId).andExpect(status().isOk());
        var answer = statementOf(buyer, transactionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.lotId").value(lot.getId()))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.lotPrice").value(45000.00))
                .andExpect(jsonPath("$.initialPayment").value(9000.00))
                .andExpect(jsonPath("$.financedAmount").value(36000.00))
                .andExpect(jsonPath("$.termMonths").value(12))
                .andExpect(jsonPath("$.annualInterestRate").value(12.0))
                .andExpect(jsonPath("$.totalAmount").value(38258.81))
                .andExpect(jsonPath("$.paidAmount").value(0.00))
                .andExpect(jsonPath("$.balance").value(38258.81))
                .andExpect(jsonPath("$.progressPercentage").value(0.00))
                .andExpect(jsonPath("$.fullyPaid").value(false))
                .andExpect(jsonPath("$.dueSoon").value(false))
                .andExpect(jsonPath("$.nextInstallment.number").value(1))
                .andExpect(jsonPath("$.installments", hasSize(12)))
                .andExpect(jsonPath("$.installments[0].status").value("PENDING"))
                .andExpect(jsonPath("$.installments[0].penalty").value(0.00))
                .andExpect(jsonPath("$.installments[0].amountDue").value(3188.23))
                .andExpect(jsonPath("$.installments[11].amount").value(3188.28))
                .andReturn().getResponse().getContentAsString();

        var quotedAmounts = JsonPath.<List<Number>>read(quotation, "$.installments[*].amount");
        var agreedAmounts = JsonPath.<List<Number>>read(answer, "$.installments[*].amount");
        assertEquals(quotedAmounts.stream().map(Number::doubleValue).toList(),
                agreedAmounts.stream().map(Number::doubleValue).toList(), "the agreed schedule is the quoted one");
        var openedOn = LocalDate.ofInstant(Instant.parse(JsonPath.read(answer, "$.openedAt")),
                AccountStatement.SALES_ZONE);
        assertEquals(openedOn.plusMonths(1).toString(), JsonPath.read(answer, "$.installments[0].dueDate"));
        assertEquals(openedOn.plusMonths(12).toString(), JsonPath.read(answer, "$.installments[11].dueDate"));

        acknowledge(buyer, contractId).andExpect(status().isOk());
        statementOf(buyer, transactionId)
                .andExpect(jsonPath("$.id").value(JsonPath.<Integer>read(answer, "$.id")))
                .andExpect(jsonPath("$.openedAt").value(JsonPath.<String>read(answer, "$.openedAt")));
    }

    @Test
    void onlyTheBuyerReadsTheirStatement() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var otherBuyer = BUYERS.incrementAndGet();
        var lot = lot();
        var transactionId = separate(buyer, lot, quote(buyer, lot));
        approve(sendVoucher(transactionId));
        acknowledge(buyer, issue(buyer, transactionId)).andExpect(status().isOk());

        statementOf(otherBuyer, transactionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        statementOf(buyer, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/reservations/{id}/account-statement", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reservations/{id}/account-statement", transactionId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void payingOffDoesNotReplaceTheVerifiedSignatureRequiredToSellTheLot() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot();
        var transactionId = separate(buyer, lot, quote(buyer, lot));
        approve(sendVoucher(transactionId));
        acknowledge(buyer, issue(buyer, transactionId)).andExpect(status().isOk());
        var statementId = statementIdOf(buyer, transactionId);

        var paidAt = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        pay(statementId, 2, "{\"amount\": 3188.23, \"paidAt\": \"%s\"}".formatted(paidAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(statementId))
                .andExpect(jsonPath("$.installments[1].status").value("PAID"))
                .andExpect(jsonPath("$.installments[1].paidAmount").value(3188.23))
                .andExpect(jsonPath("$.installments[1].paidAt").value(paidAt.toString()))
                .andExpect(jsonPath("$.paidAmount").value(3188.23))
                .andExpect(jsonPath("$.balance").value(35070.58))
                .andExpect(jsonPath("$.nextInstallment.number").value(1))
                .andExpect(jsonPath("$.fullyPaid").value(false));
        statementOf(buyer, transactionId)
                .andExpect(jsonPath("$.installments[1].status").value("PAID"))
                .andExpect(jsonPath("$.balance").value(35070.58));

        pay(statementId, 2, "{\"amount\": 3188.23}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTALLMENT_CONFLICT"));
        pay(statementId, 1, "{\"amount\": 3000}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.details").value(containsString("3188.23 PEN")));

        for (var number = 1; number <= 11; number++) {
            if (number != 2) {
                pay(statementId, number, "{\"amount\": 3188.23}").andExpect(status().isOk());
            }
        }
        assertEquals(LotStatus.RESERVED, lotRepository.findById(lot.getId()).orElseThrow().getStatus());
        pay(statementId, 12, "{\"amount\": 3188.28}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyPaid").value(true))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.paidAmount").value(38258.81))
                .andExpect(jsonPath("$.progressPercentage").value(100.00))
                .andExpect(jsonPath("$.nextInstallment", nullValue()))
                .andExpect(jsonPath("$.dueSoon").value(false));

        assertEquals(LotStatus.RESERVED, lotRepository.findById(lot.getId()).orElseThrow().getStatus());
        mockMvc.perform(get("/api/v1/projects/{id}/lots", lot.getProjectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].properties.status").value("RESERVED"));
        statementOf(buyer, transactionId).andExpect(jsonPath("$.fullyPaid").value(true));
    }

    @Test
    void aPaymentNeedsAnExistingInstallmentAPastDateAndTheBackOffice() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot();
        var transactionId = separate(buyer, lot, quote(buyer, lot));
        approve(sendVoucher(transactionId));
        acknowledge(buyer, issue(buyer, transactionId)).andExpect(status().isOk());
        var statementId = statementIdOf(buyer, transactionId);

        pay(999_999L, 1, "{\"amount\": 3188.23}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATEMENT_NOT_FOUND"));
        pay(statementId, 13, "{\"amount\": 3188.23}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INSTALLMENT_NOT_FOUND"));
        pay(statementId, 1, "{\"amount\": 3188.23, \"paidAt\": \"%s\"}"
                .formatted(Instant.now().plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        pay(statementId, 1, "{\"amount\": -5}").andExpect(status().isBadRequest());
        pay(statementId, 1, "{}").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/account-statements/{id}/installments/1/payment", statementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 3188.23}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/account-statements/{id}/installments/1/payment", statementId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 3188.23}"))
                .andExpect(status().isUnauthorized());

        statementOf(buyer, transactionId)
                .andExpect(jsonPath("$.paidAmount").value(0.00))
                .andExpect(jsonPath("$.installments[0].status").value("PENDING"));
    }

    @Test
    void aBuyerSeesAllTheirLotsConsolidated() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var firstLot = lot();
        var first = separate(buyer, firstLot, quote(buyer, firstLot));
        approve(sendVoucher(first));
        acknowledge(buyer, issue(buyer, first)).andExpect(status().isOk());
        var secondLot = lot();
        var second = separate(buyer, secondLot, quote(buyer, secondLot));
        approve(sendVoucher(second));
        acknowledge(buyer, issue(buyer, second)).andExpect(status().isOk());
        pay(statementIdOf(buyer, first), 1, "{\"amount\": 3188.23}").andExpect(status().isOk());

        mine(buyer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals", hasSize(1)))
                .andExpect(jsonPath("$.totals[0].currency").value("PEN"))
                .andExpect(jsonPath("$.totals[0].lots").value(2))
                .andExpect(jsonPath("$.totals[0].invested").value(21188.23))
                .andExpect(jsonPath("$.totals[0].debt").value(73329.39))
                .andExpect(jsonPath("$.totals[0].progressPercentage").value(22.42))
                .andExpect(jsonPath("$.statements", hasSize(2)))
                .andExpect(jsonPath("$.statements[0].transactionId").value(first.toString()))
                .andExpect(jsonPath("$.statements[0].projectId").value(firstLot.getProjectId()))
                .andExpect(jsonPath("$.statements[0].projectName").value("Estados de cuenta"))
                .andExpect(jsonPath("$.statements[0].lotId").value(firstLot.getId()))
                .andExpect(jsonPath("$.statements[0].lotCode").value("E-01"))
                .andExpect(jsonPath("$.statements[0].lotPrice").value(45000.00))
                .andExpect(jsonPath("$.statements[0].paidAmount").value(3188.23))
                .andExpect(jsonPath("$.statements[0].balance").value(35070.58))
                .andExpect(jsonPath("$.statements[0].overdueInstallments").value(0))
                .andExpect(jsonPath("$.statements[0].nextInstallment.number").value(2))
                .andExpect(jsonPath("$.statements[0].nextInstallment.amountDue").value(3188.23))
                .andExpect(jsonPath("$.statements[0].nextInstallment.status").value("PENDING"))
                .andExpect(jsonPath("$.statements[0].dueSoon").value(false))
                .andExpect(jsonPath("$.statements[0].fullyPaid").value(false))
                .andExpect(jsonPath("$.statements[1].transactionId").value(second.toString()))
                .andExpect(jsonPath("$.statements[1].lotId").value(secondLot.getId()))
                .andExpect(jsonPath("$.statements[1].paidAmount").value(0.00))
                .andExpect(jsonPath("$.statements[1].progressPercentage").value(0.00))
                .andExpect(jsonPath("$.statements[1].nextInstallment.number").value(1));
    }

    @Test
    void theConsolidatedViewOnlyShowsTheCallersLots() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var otherBuyer = BUYERS.incrementAndGet();
        var lot = lot();
        var transactionId = separate(buyer, lot, quote(buyer, lot));
        approve(sendVoucher(transactionId));
        acknowledge(buyer, issue(buyer, transactionId)).andExpect(status().isOk());

        mine(otherBuyer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals", hasSize(0)))
                .andExpect(jsonPath("$.statements", hasSize(0)));
        mine(buyer).andExpect(jsonPath("$.statements", hasSize(1)));
        mockMvc.perform(get("/api/v1/account-statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/account-statements")).andExpect(status().isUnauthorized());
    }

    /** Web reservations made before the plan was kept have nothing to schedule: the agreement still works. */
    @Test
    void aReservationWithoutPlanIsAgreedWithoutStatement() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot();
        var transactionId = UUID.randomUUID();
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var reservation = reservationRepository.save(Reservation.restore(null, lot.getId(), ReservationChannel.WEB,
                buyer, null, transactionId, Money.of(new BigDecimal("9000")), now, ReservationStatus.VERIFIED,
                List.of(), now, null));
        var contract = contractRepository.save(Contract.restore(null, reservation.getId(), transactionId, buyer,
                lot.getId(), UUID.randomUUID(), "contracts/%s/legacy.pdf".formatted(transactionId), 3000,
                ContractStatus.ISSUED, now, 77L, null));

        acknowledge(buyer, contract.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedAt", notNullValue()));

        assertTrue(accountStatementRepository.findByContractId(contract.getId()).isEmpty());
        statementOf(buyer, transactionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATEMENT_NOT_FOUND"));
    }

    private ResultActions statementOf(long buyer, UUID transactionId) throws Exception {
        return mockMvc.perform(get("/api/v1/reservations/{id}/account-statement", transactionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)));
    }

    private ResultActions mine(long buyer) throws Exception {
        return mockMvc.perform(get("/api/v1/account-statements")
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)));
    }

    private long statementIdOf(long buyer, UUID transactionId) throws Exception {
        var answer = statementOf(buyer, transactionId).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(answer, "$.id").longValue();
    }

    private ResultActions pay(long statementId, int number, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/account-statements/{id}/installments/{number}/payment",
                        statementId, number)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions acknowledge(long buyer, long contractId) throws Exception {
        return mockMvc.perform(post("/api/v1/contracts/{id}/acknowledgment", contractId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)));
    }

    /** Simulates 9 000 down in 12 months; returns the quotation as answered. */
    private String quote(long buyer, Lot lot) throws Exception {
        return mockMvc.perform(post("/api/v1/lots/{lotId}/quotations", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPayment\": 9000, \"termMonths\": 12}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Separates the lot with the quotation; returns the transaction id. */
    private UUID separate(long buyer, Lot lot, String quotation) throws Exception {
        var separation = mockMvc.perform(post("/api/v1/lots/{lotId}/separation-requests", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quotationId\": %d}".formatted(JsonPath.<Integer>read(quotation, "$.id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(separation, "$.transactionId"));
    }

    private Long sendVoucher(UUID transactionId) {
        var voucherId = UUID.randomUUID();
        return reservationCommandService.handle(new ReceiveVoucherEvidenceCommand(transactionId, voucherId,
                Money.of(new BigDecimal("9000")), LocalDate.now().minusDays(1), "TRX-1", false,
                "vouchers/%s/%s.jpg".formatted(transactionId, voucherId), Instant.now())).getId();
    }

    private void approve(Long evidenceId) throws Exception {
        mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L)))
                .andExpect(status().isOk());
    }

    /** The back office uploads the PDF with a presigned URL and issues the contract; returns its id. */
    private long issue(long buyer, UUID transactionId) throws Exception {
        var pdf = new byte[3000];
        System.arraycopy("%PDF-1.7".getBytes(), 0, pdf, 0, 8);
        var documentId = UUID.randomUUID();
        var body = "{\"documentId\": \"%s\", \"sizeBytes\": %d}".formatted(documentId, pdf.length);
        var answer = mockMvc.perform(post("/api/v1/reservations/{id}/contract/upload-url", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var upload = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                        .header("Content-Type", "application/pdf")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(pdf))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, upload.statusCode(), upload.body());
        mockMvc.perform(post("/api/v1/reservations/{id}/contract", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        var contract = mockMvc.perform(get("/api/v1/reservations/{id}/contract", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(contract, "$.contractId").longValue();
    }

    /** A lot of its own published project, at 12 % a year with a 20 % minimum down payment. */
    private Lot lot() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Estados de cuenta", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "E-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
