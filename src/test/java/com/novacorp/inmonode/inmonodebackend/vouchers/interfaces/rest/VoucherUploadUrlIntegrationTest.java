package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RecordFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.ReservationOperationCommandService;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Presigned upload of voucher files (US-33) against a real PostgreSQL and a real S3-compatible storage: the agent
 * asks for a URL for one of their reservations and uploads the file straight to the storage with it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class VoucherUploadUrlIntegrationTest {

    private static final AtomicLong AGENTS = new AtomicLong(5_000);
    private static final int FIVE_MB = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ReservationOperationCommandService operationCommandService;

    @Autowired
    private ObjectStorage objectStorage;

    @Test
    void anAgentUploadsTheVoucherOfTheirReservationWithTheIssuedUrl() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);
        var voucher = UUID.randomUUID();
        var before = Instant.now();

        var response = requestUrl(agent, voucher, reservation, "image/jpeg", 2048)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("vouchers/%s/%s.jpg".formatted(reservation, voucher)))
                .andExpect(jsonPath("$.headers['Content-Type']").value("image/jpeg"))
                .andExpect(jsonPath("$.headers['Content-Length']").value("2048"))
                .andReturn().getResponse().getContentAsString();

        var expiresAt = Instant.parse(JsonPath.read(response, "$.expiresAt"));
        assertTrue(expiresAt.isAfter(before.plus(Duration.ofMinutes(9))), "valid for 10 minutes");
        assertTrue(expiresAt.isBefore(before.plus(Duration.ofMinutes(11))), "valid for 10 minutes");
        var upload = upload(response, new byte[2048]);
        assertEquals(200, upload.statusCode(), upload.body());
        var stored = objectStorage.describe(JsonPath.read(response, "$.objectKey")).orElseThrow();
        assertEquals(2048, stored.sizeBytes());
        assertEquals("image/jpeg", stored.contentType());
    }

    @Test
    void theStorageRejectsAFileLargerThanTheDeclaredOne() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var response = requestUrl(agent, UUID.randomUUID(), operationOf(agent), "image/jpeg", 2048)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var upload = upload(response, new byte[4096]);

        assertEquals(403, upload.statusCode(), upload.body());
        assertTrue(objectStorage.describe(JsonPath.read(response, "$.objectKey")).isEmpty());
    }

    @Test
    void pngAndPdfUpToFiveMegabytesAreAcceptedToo() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);
        var voucher = UUID.randomUUID();

        requestUrl(agent, voucher, reservation, "image/png", FIVE_MB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("vouchers/%s/%s.png".formatted(reservation, voucher)))
                .andExpect(jsonPath("$.headers['Content-Length']").value(String.valueOf(FIVE_MB)));
        requestUrl(agent, voucher, reservation, " Application/PDF", 2048)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("vouchers/%s/%s.pdf".formatted(reservation, voucher)))
                .andExpect(jsonPath("$.headers['Content-Type']").value("application/pdf"));
    }

    @Test
    void otherFormatsAndFilesOverFiveMegabytesAreRejected() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);

        requestUrl(agent, UUID.randomUUID(), reservation, "text/plain", 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(containsString("contentType")));
        requestUrl(agent, UUID.randomUUID(), reservation, "image/jpeg", 6 * 1024 * 1024)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("sizeBytes")));
        requestUrl(agent, UUID.randomUUID(), reservation, "image/jpeg", FIVE_MB + 1)
                .andExpect(status().isBadRequest());
        requestUrl(agent, UUID.randomUUID(), reservation, "image/jpeg", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("sizeBytes")));
        perform(bearer(Role.FIELD_AGENT, agent), """
                {"reservationId": "%s", "contentType": "image/jpeg", "sizeBytes": 2048}""".formatted(reservation))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("voucherId")));
    }

    @Test
    void aReservationOfAnotherAgentCannotBeToldApartFromAMissingOne() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var otherAgent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);

        requestUrl(otherAgent, UUID.randomUUID(), reservation, "image/jpeg", 2048)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_OPERATION_NOT_FOUND"));
        requestUrl(agent, UUID.randomUUID(), UUID.randomUUID(), "image/jpeg", 2048)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_OPERATION_NOT_FOUND"));
    }

    @Test
    void onlyFieldAgentsAskForUploadUrls() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var body = body(UUID.randomUUID(), operationOf(agent), "image/jpeg", 2048);

        perform(bearer(Role.BUYER, agent), body).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/vouchers/upload-url").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    /** A reservation the agent synchronized and that took its lot, as "Lote separado" leaves it. */
    private UUID operationOf(long agent) {
        var reservation = UUID.randomUUID();
        operationCommandService.handle(new RecordFieldReservationCommand(reservation, agent, 1L,
                new BigDecimal("1500.00"), Instant.parse("2026-10-08T09:30:00Z"),
                Instant.now().plus(Duration.ofHours(24))));
        return reservation;
    }

    private ResultActions requestUrl(long agent, UUID voucher, UUID reservation, String contentType, long sizeBytes)
            throws Exception {
        return perform(bearer(Role.FIELD_AGENT, agent), body(voucher, reservation, contentType, sizeBytes));
    }

    private ResultActions perform(String authorization, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/vouchers/upload-url")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String body(UUID voucher, UUID reservation, String contentType, long sizeBytes) {
        return """
                {"voucherId": "%s", "reservationId": "%s", "contentType": "%s", "sizeBytes": %d}"""
                .formatted(voucher, reservation, contentType, sizeBytes);
    }

    /** The PUT a client makes with the answer; Content-Length comes from the body, as in any HTTP client. */
    private HttpResponse<String> upload(String answer, byte[] file) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                .header("Content-Type", JsonPath.<String>read(answer, "$.headers['Content-Type']"))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
