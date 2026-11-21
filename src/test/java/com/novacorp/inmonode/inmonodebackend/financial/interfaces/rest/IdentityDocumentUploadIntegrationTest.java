package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingPlan;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The field agent uploads the photo of the buyer's identity document of a field reservation (US-59) and registers
 * it, against a real PostgreSQL and a real S3-compatible storage. Each test uses its own agent and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class IdentityDocumentUploadIntegrationTest {

    private static final AtomicLong AGENTS = new AtomicLong(6_000);
    private static final int FIVE_MB = 5 * 1024 * 1024;

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
    private FieldReservationConsolidationFacade fieldFacade;

    @Autowired
    private ObjectStorage objectStorage;

    @Test
    void anAgentUploadsAndRegistersTheIdentityDocumentOfTheirReservation() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = fieldReservationOf(agent);
        var photo = UUID.randomUUID();
        var before = Instant.now();
        assertTrue(reservationRepository.findBySourceEventId(reservation).orElseThrow().isIdentityDocumentPending());

        var answer = call(agent, reservation, "/upload-url", photo, "image/jpeg", 2048)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("identity-documents/%s/%s.jpg".formatted(reservation, photo)))
                .andExpect(jsonPath("$.headers['Content-Type']").value("image/jpeg"))
                .andExpect(jsonPath("$.headers['Content-Length']").value("2048"))
                .andReturn().getResponse().getContentAsString();
        var expiresAt = Instant.parse(JsonPath.read(answer, "$.expiresAt"));
        assertTrue(expiresAt.isAfter(before.plus(Duration.ofMinutes(9))), "valid for 10 minutes");
        assertTrue(expiresAt.isBefore(before.plus(Duration.ofMinutes(11))), "valid for 10 minutes");
        var upload = upload(answer, new byte[2048]);
        assertEquals(200, upload.statusCode(), upload.body());

        var registered = register(agent, reservation, photo, "image/jpeg", 2048)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(reservation.toString()))
                .andExpect(jsonPath("$.documentId").value(photo.toString()))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.sizeBytes").value(2048))
                .andExpect(jsonPath("$.result").value("RECEIVED"))
                .andReturn().getResponse().getContentAsString();

        var stored = reservationRepository.findBySourceEventId(reservation).orElseThrow();
        assertFalse(stored.isIdentityDocumentPending());
        assertEquals(photo, stored.getIdentityDocument().documentId());
        register(agent, reservation, photo, "image/jpeg", 2048)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("DUPLICATE"))
                .andExpect(jsonPath("$.attachedAt").value(JsonPath.<String>read(registered, "$.attachedAt")));
    }

    @Test
    void theRegisteredPhotoCannotBeReplaced() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = fieldReservationOf(agent);
        var photo = UUID.randomUUID();
        uploadAndRegister(agent, reservation, photo);

        call(agent, reservation, "/upload-url", UUID.randomUUID(), "image/jpeg", 2048)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_DOCUMENT_CONFLICT"));
        register(agent, reservation, UUID.randomUUID(), "image/jpeg", 2048)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_DOCUMENT_CONFLICT"));
        assertEquals(photo,
                reservationRepository.findBySourceEventId(reservation).orElseThrow().getIdentityDocument().documentId());
    }

    @Test
    void aPhotoThatIsNotInTheRepositoryOrDiffersFromTheDeclaredOneIsNotRegistered() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = fieldReservationOf(agent);
        var photo = UUID.randomUUID();

        register(agent, reservation, photo, "image/jpeg", 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_DOCUMENT_FILE_NOT_UPLOADED"));
        var answer = call(agent, reservation, "/upload-url", photo, "image/jpeg", 2048)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(200, upload(answer, new byte[2048]).statusCode());
        register(agent, reservation, photo, "image/jpeg", 4096)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_DOCUMENT_FILE_NOT_UPLOADED"));
        register(agent, reservation, photo, "image/png", 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_DOCUMENT_FILE_NOT_UPLOADED"));

        assertTrue(reservationRepository.findBySourceEventId(reservation).orElseThrow().isIdentityDocumentPending());
    }

    @Test
    void onlyJpegOrPngPhotosUpToFiveMegabytesAreAccepted() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = fieldReservationOf(agent);
        var photo = UUID.randomUUID();

        call(agent, reservation, "/upload-url", photo, " Image/PNG", FIVE_MB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("identity-documents/%s/%s.png".formatted(reservation, photo)))
                .andExpect(jsonPath("$.headers['Content-Type']").value("image/png"));
        call(agent, reservation, "/upload-url", photo, "application/pdf", 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(containsString("contentType")));
        call(agent, reservation, "/upload-url", photo, "image/jpeg", FIVE_MB + 1)
                .andExpect(status().isBadRequest());
        call(agent, reservation, "/upload-url", photo, "image/jpeg", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("sizeBytes")));
        perform(bearer(Role.FIELD_AGENT, agent), reservation, "/upload-url", """
                {"contentType": "image/jpeg", "sizeBytes": 2048}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("documentId")));
    }

    @Test
    void aReservationOfAnotherAgentOrFromTheWebCannotBeToldApartFromAMissingOne() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var otherAgent = AGENTS.incrementAndGet();
        var reservation = fieldReservationOf(agent);
        var web = UUID.randomUUID();
        reservationRepository.save(Reservation.fromWebRequest(lot("D-02").getId(), agent, web,
                Money.of(new BigDecimal("9000")), new FinancingPlan(Money.of(new BigDecimal("45000")), 12,
                        new BigDecimal("12")), Instant.now()));

        for (var unknown : List.of(reservation, UUID.randomUUID())) {
            call(otherAgent, unknown, "/upload-url", UUID.randomUUID(), "image/jpeg", 2048)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        }
        call(agent, web, "/upload-url", UUID.randomUUID(), "image/jpeg", 2048)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        register(otherAgent, reservation, UUID.randomUUID(), "image/jpeg", 2048)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void staffOtherThanFieldAgentsCannotUploadIdentityDocuments() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = fieldReservationOf(agent);
        var body = body(UUID.randomUUID(), "image/jpeg", 2048);

        perform(bearer(Role.CATALOG_ADMIN, agent), reservation, "/upload-url", body)
                .andExpect(status().isForbidden());
        perform(bearer(Role.FINANCE_ADMIN, agent), reservation, "", body)
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path(reservation, "/upload-url")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    /** A field reservation the agent synchronized, as "Lote separado" leaves it. */
    private UUID fieldReservationOf(long agent) {
        var reservation = UUID.randomUUID();
        fieldFacade.consolidate(reservation, lot("D-01").getId(), agent, UUID.randomUUID(), new BigDecimal("1500"),
                Instant.now());
        return reservation;
    }

    private void uploadAndRegister(long agent, UUID reservation, UUID photo) throws Exception {
        var answer = call(agent, reservation, "/upload-url", photo, "image/jpeg", 2048)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(200, upload(answer, new byte[2048]).statusCode());
        register(agent, reservation, photo, "image/jpeg", 2048).andExpect(status().isCreated());
        assertTrue(objectStorage.describe(JsonPath.read(answer, "$.objectKey")).isPresent());
    }

    private ResultActions register(long agent, UUID reservation, UUID photo, String contentType, long sizeBytes)
            throws Exception {
        return call(agent, reservation, "", photo, contentType, sizeBytes);
    }

    private ResultActions call(long agent, UUID reservation, String suffix, UUID photo, String contentType,
                               long sizeBytes) throws Exception {
        return perform(bearer(Role.FIELD_AGENT, agent), reservation, suffix, body(photo, contentType, sizeBytes));
    }

    private ResultActions perform(String authorization, UUID reservation, String suffix, String body)
            throws Exception {
        return mockMvc.perform(post(path(reservation, suffix))
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String path(UUID reservation, String suffix) {
        return "/api/v1/field-sync/reservations/%s/identity-document%s".formatted(reservation, suffix);
    }

    private static String body(UUID photo, String contentType, long sizeBytes) {
        return """
                {"documentId": "%s", "contentType": "%s", "sizeBytes": %d}""".formatted(photo, contentType, sizeBytes);
    }

    /** The PUT a client makes with the answer; Content-Length comes from the body, as in any HTTP client. */
    private HttpResponse<String> upload(String answer, byte[] file) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                        .header("Content-Type", JsonPath.<String>read(answer, "$.headers['Content-Type']"))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(file))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Lot lot(String code) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Documentos", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, code,
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
