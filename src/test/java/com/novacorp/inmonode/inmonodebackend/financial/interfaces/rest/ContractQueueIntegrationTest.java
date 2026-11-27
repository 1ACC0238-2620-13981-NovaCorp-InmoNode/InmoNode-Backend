package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The finance back office finds the contracts it has to issue (US-21) over HTTP against a real PostgreSQL and S3, so
 * its portal can list them without knowing the transaction ids. Every test uses its own lots; other tests leave
 * verified reservations behind too, so each one looks only for its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class ContractQueueIntegrationTest {

    private static final long ISSUER = 77L;
    private static final long AGENT = 7L;

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ReservationCommandService reservationCommandService;

    @Autowired
    private LotAvailabilityFacade lotAvailabilityFacade;

    @Autowired
    private FieldReservationConsolidationFacade fieldFacade;

    @Test
    void theQueueListsTheVerifiedWebReservationsUntilTheirContractIsIssued() throws Exception {
        var buyer = buyer();
        var lot = lot("Q-01");
        var verified = webReservation(lot, buyer.getId());
        approve(sendVoucher(verified, "9000"));
        designateCoOwner(verified, buyer.getId());
        var waiting = webReservation(lot("Q-02"), buyer.getId());
        sendVoucher(waiting, "9000");
        var field = UUID.randomUUID();
        fieldFacade.consolidate(field, lot("Q-03").getId(), AGENT, UUID.randomUUID(), new BigDecimal("1500"),
                Instant.now());
        approve(sendVoucher(field, "1500"));

        var queue = queue();

        var entry = entry(queue, verified);
        assertNotNull(entry, "a verified web reservation without a contract is listed");
        assertEquals(buyer.getId(), ((Number) entry.get("buyerId")).longValue());
        assertEquals(buyer.getEmail(), entry.get("buyerEmail"));
        assertEquals(lot.getProjectId(), ((Number) entry.get("projectId")).longValue());
        assertEquals("Contratos", entry.get("projectName"));
        assertEquals(lot.getId(), ((Number) entry.get("lotId")).longValue());
        assertEquals("Q-01", entry.get("lotCode"));
        assertEquals(9000.0, ((Number) entry.get("initialAmount")).doubleValue());
        assertEquals("PEN", entry.get("currency"));
        assertNotNull(entry.get("verifiedAt"));
        assertEquals(Map.of("fullName", "Ana Pérez Quispe", "documentType", "DNI", "documentNumber", "45678912"),
                entry.get("coOwner"));
        assertNull(entry(queue, waiting), "its payment is not verified yet");
        assertNull(entry(queue, field), "field reservations do not get a contract yet");

        issueContract(verified);

        assertNull(entry(queue(), verified), "an issued contract leaves the queue");
    }

    @Test
    void theQueueComesInPagesOldestVerificationFirst() throws Exception {
        var buyer = buyer().getId();
        var first = verifiedWebReservation(lot("P-01"), buyer);
        var second = verifiedWebReservation(lot("P-02"), buyer);
        var third = verifiedWebReservation(lot("P-03"), buyer);
        var everything = transactionIds(queue());
        var total = everything.size();

        var paged = new ArrayList<String>();
        for (var page = 1; (page - 1) * 2 < total; page++) {
            var body = queuePage(page, 2)
                    .andExpect(status().isOk())
                    .andExpect(header().string("Total-Count", String.valueOf(total)))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.totalCount").value(total))
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.limit").value(2))
                    .andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> items = JsonPath.read(body, "$.items");
            assertTrue(items.size() <= 2);
            paged.addAll(transactionIds(items));
        }

        assertEquals(everything, paged, "pages of 2 are consecutive slices of the whole queue");
        var firstAt = everything.indexOf(first.toString());
        var secondAt = everything.indexOf(second.toString());
        var thirdAt = everything.indexOf(third.toString());
        assertTrue(firstAt >= 0 && firstAt < secondAt && secondAt < thirdAt, "oldest verification first");
        queuePage(total / 2 + 2, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalCount").value(total));
        queuePage(1, 5000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(100));
        mockMvc.perform(get("/api/v1/contracts/to-issue").header(HttpHeaders.AUTHORIZATION, finance()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20));
        queuePage(0, 20).andExpect(status().isBadRequest());
        queuePage(1, 0).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheFinanceBackOfficeSeesTheQueue() throws Exception {
        mockMvc.perform(get("/api/v1/contracts/to-issue").header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, 41L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/contracts/to-issue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FIELD_AGENT, AGENT)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/contracts/to-issue"))
                .andExpect(status().isUnauthorized());
    }

    /** Every entry, reading the queue page by page. */
    private List<Map<String, Object>> queue() throws Exception {
        var entries = new ArrayList<Map<String, Object>>();
        for (var page = 1; ; page++) {
            var body = queuePage(page, 100)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> items = JsonPath.read(body, "$.items");
            entries.addAll(items);
            if (items.size() < 100) {
                return entries;
            }
        }
    }

    private ResultActions queuePage(int page, int limit) throws Exception {
        return mockMvc.perform(get("/api/v1/contracts/to-issue")
                .param("page", String.valueOf(page))
                .param("limit", String.valueOf(limit))
                .header(HttpHeaders.AUTHORIZATION, finance()));
    }

    private static Map<String, Object> entry(List<Map<String, Object>> queue, UUID transactionId) {
        return queue.stream()
                .filter(entry -> transactionId.toString().equals(entry.get("transactionId")))
                .findFirst()
                .orElse(null);
    }

    private static List<String> transactionIds(List<Map<String, Object>> entries) {
        return entries.stream().map(entry -> (String) entry.get("transactionId")).toList();
    }

    /** Uploads a PDF and issues the contract with it, as the portal does after taking an entry of the queue. */
    private void issueContract(UUID transactionId) throws Exception {
        var documentId = UUID.randomUUID();
        var pdf = new byte[2048];
        System.arraycopy("%PDF-1.7".getBytes(), 0, pdf, 0, 8);
        var body = "{\"documentId\": \"%s\", \"sizeBytes\": %d}".formatted(documentId, pdf.length);
        var answer = mockMvc.perform(post("/api/v1/reservations/{id}/contract/upload-url", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, finance())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var upload = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                        .header("Content-Type", "application/pdf")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(pdf))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, upload.statusCode());
        mockMvc.perform(post("/api/v1/reservations/{id}/contract", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, finance())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private void designateCoOwner(UUID transactionId, Long buyerId) throws Exception {
        mockMvc.perform(post("/api/v1/reservations/{id}/co-owner", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Ana Pérez Quispe\", \"documentType\": \"DNI\", "
                                + "\"documentNumber\": \"45678912\"}"))
                .andExpect(status().isOk());
    }

    /** A web separation of 9 000 down whose voucher the back office approved. */
    private UUID verifiedWebReservation(Lot lot, Long buyerId) throws Exception {
        var transactionId = webReservation(lot, buyerId);
        approve(sendVoucher(transactionId, "9000"));
        return transactionId;
    }

    private UUID webReservation(Lot lot, Long buyerId) {
        var transactionId = UUID.randomUUID();
        assertEquals("BLOCKED", lotAvailabilityFacade.blockLot(transactionId, lot.getId(), buyerId,
                new BigDecimal("9000"), "PEN", 12, new BigDecimal("12"), Instant.now()).result());
        return transactionId;
    }

    private Long sendVoucher(UUID reservationId, String amount) {
        var voucherId = UUID.randomUUID();
        return reservationCommandService.handle(new ReceiveVoucherEvidenceCommand(reservationId, voucherId,
                Money.of(new BigDecimal(amount)), LocalDate.now().minusDays(1), "00123456", false,
                "vouchers/%s/%s.jpg".formatted(reservationId, voucherId), Instant.now())).getId();
    }

    private void approve(Long evidenceId) throws Exception {
        mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, finance()))
                .andExpect(status().isOk());
    }

    /** A buyer with an account, whose email the queue shows. */
    private User buyer() {
        var buyer = User.register("buyer-" + UUID.randomUUID() + "@mail.com", "hash", Role.BUYER, Instant.now());
        buyer.clearDomainEvents();
        return userRepository.save(buyer);
    }

    /** A lot of its own published project. */
    private Lot lot(String code) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Contratos", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, code,
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String finance() {
        return bearer(Role.FINANCE_ADMIN, ISSUER);
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
