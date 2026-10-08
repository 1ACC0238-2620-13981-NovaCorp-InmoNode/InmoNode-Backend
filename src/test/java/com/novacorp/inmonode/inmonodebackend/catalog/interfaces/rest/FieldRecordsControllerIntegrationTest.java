package com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.repositories.ProspectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Synchronization of the field app (US-11, US-12, US-32) over HTTP against a real PostgreSQL: prospects are stored
 * in the field context and each reservation is consolidated by financial. Each test uses its own agent and project.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FieldRecordsControllerIntegrationTest {

    private static final AtomicLong AGENTS = new AtomicLong(1_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProspectRepository prospectRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void eachReservationGetsItsOwnResultAndAcceptedOnesHoldTheirLots() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var projectId = publishedProject();
        var lots = lotIdsByCode(projectId);
        var ana = UUID.randomUUID();
        var luis = UUID.randomUUID();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var third = UUID.randomUUID();

        sync(agent, payload(List.of(prospect(ana, "12345678", "Ana Quispe"), prospect(luis, "87654321", "Luis Mamani")),
                List.of(reservation(first, lots.get("A-01"), ana), reservation(second, lots.get("A-02"), luis),
                        reservation(third, lots.get("A-01"), luis))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prospectsSynced").value(2))
                .andExpect(jsonPath("$.reservations.length()").value(3))
                .andExpect(jsonPath("$.reservations[0].id").value(first.toString()))
                .andExpect(jsonPath("$.reservations[0].result").value("SYNCED"))
                .andExpect(jsonPath("$.reservations[0].reservationStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.reservations[0].blockedUntil").isNotEmpty())
                .andExpect(jsonPath("$.reservations[1].result").value("SYNCED"))
                .andExpect(jsonPath("$.reservations[2].id").value(third.toString()))
                .andExpect(jsonPath("$.reservations[2].result").value("CONFLICT"))
                .andExpect(jsonPath("$.reservations[2].conflictReason").value("LOT_UNAVAILABLE"));

        var portfolio = mockMvc.perform(get("/api/v1/field-sync/portfolio").header(HttpHeaders.AUTHORIZATION,
                        bearer(Role.FIELD_AGENT, agent)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> statuses = JsonPath.read(portfolio,
                "$.projects[?(@.id == %d)].lots.features[*].properties.status".formatted(projectId));
        assertEquals(List.of("BLOCKED", "BLOCKED", "AVAILABLE"), statuses);
        var catalog = mockMvc.perform(get("/api/v1/projects")).andReturn().getResponse().getContentAsString();
        List<Double> availability = JsonPath.read(catalog,
                "$[?(@.id == %d)].availabilityPercentage".formatted(projectId));
        assertEquals(List.of(33.33), availability);
    }

    @Test
    void resendingTheSameSyncAnswersEveryReservationAsADuplicate() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var lots = lotIdsByCode(publishedProject());
        var ana = UUID.randomUUID();
        var body = payload(List.of(prospect(ana, "12345678", "Ana Quispe")),
                List.of(reservation(UUID.randomUUID(), lots.get("A-01"), ana),
                        reservation(UUID.randomUUID(), lots.get("A-01"), ana)));
        sync(agent, body).andExpect(status().isOk());

        sync(agent, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prospectsSynced").value(1))
                .andExpect(jsonPath("$.reservations[0].result").value("DUPLICATE"))
                .andExpect(jsonPath("$.reservations[0].originalResult").value("SYNCED"))
                .andExpect(jsonPath("$.reservations[0].blockedUntil").isNotEmpty())
                .andExpect(jsonPath("$.reservations[1].result").value("DUPLICATE"))
                .andExpect(jsonPath("$.reservations[1].originalResult").value("CONFLICT"));
        assertEquals(1, prospectRepository.findByProspectIds(List.of(ana)).size());
    }

    @Test
    void aStructurallyInvalidSyncIsRejectedWithItsIndexAndNothingIsStored() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var lots = lotIdsByCode(publishedProject());
        var ana = UUID.randomUUID();
        var valid = UUID.randomUUID();

        sync(agent, payload(List.of(prospect(ana, "12345678", "Ana Quispe")),
                List.of(reservation(valid, lots.get("A-01"), ana), reservation(UUID.randomUUID(), null, ana))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(containsString("reservations[1].lotId")));

        assertTrue(prospectRepository.findByProspectIds(List.of(ana)).isEmpty());
        assertTrue(reservationRepository.findBySourceEventId(valid).isEmpty());
    }

    @Test
    void reservationsMustNameAProspectOfTheSyncOrOneTheAgentSentBefore() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var otherAgent = AGENTS.incrementAndGet();
        var lots = lotIdsByCode(publishedProject());
        var sentBefore = UUID.randomUUID();
        var ofAnotherAgent = UUID.randomUUID();
        sync(agent, payload(List.of(prospect(sentBefore, "12345678", "Ana Quispe")), List.of()))
                .andExpect(jsonPath("$.prospectsSynced").value(1));
        sync(otherAgent, payload(List.of(prospect(ofAnotherAgent, "87654321", "Luis Mamani")), List.of()));

        sync(agent, payload(List.of(), List.of(reservation(UUID.randomUUID(), lots.get("A-01"), sentBefore))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations[0].result").value("SYNCED"));
        var rejected = UUID.randomUUID();
        sync(agent, payload(List.of(), List.of(reservation(rejected, lots.get("A-02"), ofAnotherAgent))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("reservations[0].prospectId")));
        assertTrue(reservationRepository.findBySourceEventId(rejected).isEmpty());
    }

    @Test
    void onlyFieldAgentsCanSync() throws Exception {
        var body = payload(List.of(), List.of());

        sync(AGENTS.incrementAndGet(), body).andExpect(status().isOk())
                .andExpect(jsonPath("$.prospectsSynced").value(0));
        mockMvc.perform(post("/api/v1/field-sync").header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, 1L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/field-sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions sync(long agentId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/field-sync")
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.FIELD_AGENT, agentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String payload(List<String> prospects, List<String> reservations) {
        return "{\"prospects\": [%s], \"reservations\": [%s]}"
                .formatted(String.join(",", prospects), String.join(",", reservations));
    }

    private static String prospect(UUID id, String document, String fullName) {
        return """
                {"id": "%s", "document": "%s", "fullName": "%s", "phone": "987654321",
                 "registeredAt": "2026-10-08T09:00:00Z"}""".formatted(id, document, fullName);
    }

    private static String reservation(UUID id, Long lotId, UUID prospectId) {
        return """
                {"id": "%s", "lotId": %s, "prospectId": "%s", "initialAmount": 1500,
                 "reservedAt": "2026-10-08T09:30:00Z"}""".formatted(id, lotId, prospectId);
    }

    /** A published project with the 3 valid lots of the fixture plan (A-01 to A-03). */
    private Long publishedProject() throws Exception {
        var admin = bearer(Role.CATALOG_ADMIN, 1L);
        var created = mockMvc.perform(post("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Campo", "location": "Chilca, Lima",
                                 "financingRules": {"minDownPaymentPercentage": 10, "annualInterestRate": 12,
                                                    "maxTermMonths": 120, "lateFeeRate": 2}}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var projectId = ((Number) JsonPath.read(created, "$.id")).longValue();
        mockMvc.perform(post("/api/v1/projects/{id}/lots", projectId).header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ClassPathResource("fixtures/plan-los-pinos.geojson")
                                .getContentAsString(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/{id}/publish", projectId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        return projectId;
    }

    private Map<String, Long> lotIdsByCode(Long projectId) throws Exception {
        var lots = mockMvc.perform(get("/api/v1/projects/{id}/lots", projectId))
                .andReturn().getResponse().getContentAsString();
        List<String> codes = JsonPath.read(lots, "$.features[*].properties.code");
        List<Number> ids = JsonPath.read(lots, "$.features[*].id");
        var byCode = new HashMap<String, Long>();
        for (int i = 0; i < codes.size(); i++) {
            byCode.put(codes.get(i), ids.get(i).longValue());
        }
        return byCode;
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
