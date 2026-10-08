package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Financing simulations (US-17) over HTTP against a real PostgreSQL: a buyer simulates the financing of an available
 * lot with its project's rules and reads it back. Each test uses its own buyer and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class QuotationsIntegrationTest {

    private static final AtomicLong BUYERS = new AtomicLong(8_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private LotAvailabilityFacade lotAvailabilityFacade;

    @Test
    void aBuyerSimulatesTheFinancingOfAnAvailableLotWithTheProjectRules() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot(true);
        var before = Instant.now();

        var created = simulate(buyer, lot.getId(), "9000", 12)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.lotId").value(lot.getId()))
                .andExpect(jsonPath("$.projectId").value(lot.getProjectId()))
                .andExpect(jsonPath("$.lotCode").value("Q-01"))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.lotPrice").value(45000.0))
                .andExpect(jsonPath("$.initialPayment").value(9000.0))
                .andExpect(jsonPath("$.initialPercentage").value(20.0))
                .andExpect(jsonPath("$.financedAmount").value(36000.0))
                .andExpect(jsonPath("$.termMonths").value(12))
                .andExpect(jsonPath("$.annualInterestRate").value(12.0))
                .andExpect(jsonPath("$.monthlyInstallment").value(3198.56))
                .andExpect(jsonPath("$.totalInterest").value(2382.66))
                .andExpect(jsonPath("$.totalToPay").value(47382.66))
                .andExpect(jsonPath("$.installments", hasSize(12)))
                .andExpect(jsonPath("$.installments[0].number").value(1))
                .andExpect(jsonPath("$.installments[0].interest").value(360.0))
                .andExpect(jsonPath("$.installments[11].balance").value(0.0))
                .andReturn().getResponse().getContentAsString();

        var validUntil = Instant.parse(JsonPath.read(created, "$.validUntil"));
        var generatedAt = Instant.parse(JsonPath.read(created, "$.generatedAt"));
        assertFalse(generatedAt.isBefore(before.minusSeconds(1)));
        assertEquals(generatedAt.plus(Duration.ofDays(7)), validUntil, "valid for 7 days");
        var read = mockMvc.perform(get("/api/v1/quotations/{id}", JsonPath.<Integer>read(created, "$.id"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(created, read, "the stored quotation is the one that was shown");
    }

    @Test
    void aDownPaymentUnderTheMinimumIsRejectedNamingTheMinimum() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot(true);

        simulate(buyer, lot.getId(), "8999.99", 12)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(containsString("9000.00 PEN")));
        simulate(buyer, lot.getId(), "45000", 12)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("initialPayment")));
        simulate(buyer, lot.getId(), "0", 12)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("initialPayment")));
    }

    @Test
    void theTermCannotExceedTheProjectMaximum() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot(true);

        simulate(buyer, lot.getId(), "9000", 121)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("termMonths")));
        simulate(buyer, lot.getId(), "9000", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("termMonths")));
        simulate(buyer, lot.getId(), "9000", 120).andExpect(status().isCreated());
    }

    @Test
    void aLotThatIsNotAvailableCannotBeQuoted() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot(true);
        lotAvailabilityFacade.blockLot(UUID.randomUUID(), lot.getId(), 999L, new BigDecimal("9000"), "PEN",
                Instant.now());

        simulate(buyer, lot.getId(), "9000", 12)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOT_CONFLICT"));
    }

    @Test
    void lotsOfDraftProjectsAndUnknownLotsAreNotFound() throws Exception {
        var buyer = BUYERS.incrementAndGet();

        simulate(buyer, lot(false).getId(), "9000", 12)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOT_NOT_FOUND"));
        simulate(buyer, 999_999L, "9000", 12)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOT_NOT_FOUND"));
    }

    @Test
    void aQuotationOfAnotherBuyerCannotBeToldApartFromAMissingOne() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var otherBuyer = BUYERS.incrementAndGet();
        var created = simulate(buyer, lot(true).getId(), "9000", 12)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/quotations/{id}", JsonPath.<Integer>read(created, "$.id"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, otherBuyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTATION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/quotations/{id}", 999_999)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyBuyersSimulate() throws Exception {
        var lot = lot(true);
        var body = body("9000", 12);

        mockMvc.perform(post("/api/v1/lots/{lotId}/quotations", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FIELD_AGENT, 7L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/lots/{lotId}/quotations", lot.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/quotations/{id}", 1))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions simulate(long buyer, Long lotId, String initialPayment, int termMonths) throws Exception {
        return mockMvc.perform(post("/api/v1/lots/{lotId}/quotations", lotId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(initialPayment, termMonths)));
    }

    private static String body(String initialPayment, int termMonths) {
        return """
                {"initialPayment": %s, "termMonths": %d}""".formatted(initialPayment, termMonths);
    }

    /** A lot of its own project, with a 20 % minimum down payment, 12 % a year and up to 120 months. */
    private Lot lot(boolean published) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Cotizaciones", "Chilca", null, null, rules);
        if (published) {
            project.publish(1);
        }
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "Q-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
