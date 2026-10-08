package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherContentType;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherStatus;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.VoucherRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.events.PaymentVoucherReceivedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration of payment vouchers (US-20, US-10) against a real PostgreSQL and a real S3-compatible storage: the
 * agent asks for the upload URL, uploads the file straight to the storage and registers the voucher with the data
 * the app read from it, which announces "Comprobante de pago recibido".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
@RecordApplicationEvents
class VoucherRegistrationIntegrationTest {

    private static final AtomicLong AGENTS = new AtomicLong(6_000);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

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
    private VoucherRepository voucherRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    void anAgentRegistersTheVoucherTheyUploadedAndItIsAnnouncedAsReceived() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);
        var voucher = UUID.randomUUID();
        var objectKey = uploadFile(agent, voucher, reservation, "image/jpeg", 2048);
        var yesterday = yesterday();
        var before = Instant.now();

        register(agent, body(voucher, reservation, "image/jpeg", 2048, "1500.5", " pen ", yesterday,
                " 00123456 ", "0.97", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voucherId").value(voucher.toString()))
                .andExpect(jsonPath("$.reservationId").value(reservation.toString()))
                .andExpect(jsonPath("$.status").value("SYNCED"))
                .andExpect(jsonPath("$.result").value("RECEIVED"))
                .andExpect(jsonPath("$.receivedAt", notNullValue()));

        var stored = voucherRepository.findByVoucherId(voucher).orElseThrow();
        assertEquals(reservation, stored.getReservationId());
        assertEquals(objectKey, stored.getObjectKey());
        assertEquals(VoucherContentType.JPEG, stored.getContentType());
        assertEquals(2048, stored.getSizeBytes());
        assertEquals(new BigDecimal("1500.50"), stored.getExtractedData().amount());
        assertEquals("PEN", stored.getExtractedData().currency());
        assertEquals(yesterday, stored.getExtractedData().operationDate());
        assertEquals("00123456", stored.getExtractedData().operationCode());
        assertEquals(new BigDecimal("0.970"), stored.getExtractedData().confidence());
        assertFalse(stored.isManuallyCorrected());
        assertEquals(VoucherStatus.SYNCED, stored.getStatus());
        assertFalse(stored.getReceivedAt().isBefore(before.minusSeconds(1)));

        var announced = events.stream(PaymentVoucherReceivedEvent.class).toList();
        assertEquals(1, announced.size());
        var event = announced.getFirst();
        assertEquals(voucher, event.voucherId());
        assertEquals(reservation, event.reservationId());
        assertEquals(new BigDecimal("1500.50"), event.amount());
        assertEquals("PEN", event.currency());
        assertEquals(yesterday, event.operationDate());
        assertEquals("00123456", event.operationCode());
        assertFalse(event.manuallyCorrected());
        assertEquals(objectKey, event.objectKey());
        assertEquals(stored.getReceivedAt(), event.receivedAt());
    }

    @Test
    void theCorrectionFlagIsKeptAndTheConfidenceIsOptional() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);
        var voucher = UUID.randomUUID();
        uploadFile(agent, voucher, reservation, "application/pdf", 4096);

        register(agent, body(voucher, reservation, "application/pdf", 4096, "1500", "PEN", yesterday(),
                "00123456", null, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("RECEIVED"));

        var stored = voucherRepository.findByVoucherId(voucher).orElseThrow();
        assertTrue(stored.isManuallyCorrected());
        assertNull(stored.getExtractedData().confidence());
        assertTrue(events.stream(PaymentVoucherReceivedEvent.class).findFirst().orElseThrow().manuallyCorrected());
    }

    @Test
    void aVoucherWhoseFileWasNotUploadedIsRejected() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var voucher = UUID.randomUUID();

        register(agent, validBody(voucher, operationOf(agent), "image/jpeg", 2048))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VOUCHER_FILE_NOT_UPLOADED"));

        assertTrue(voucherRepository.findByVoucherId(voucher).isEmpty());
        assertEquals(0, events.stream(PaymentVoucherReceivedEvent.class).count());
    }

    @Test
    void theDeclaredFileMustBeTheUploadedOne() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);
        var voucher = UUID.randomUUID();
        uploadFile(agent, voucher, reservation, "image/jpeg", 2048);

        register(agent, validBody(voucher, reservation, "image/jpeg", 4096))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VOUCHER_FILE_NOT_UPLOADED"));
        register(agent, validBody(voucher, reservation, "image/png", 2048))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VOUCHER_FILE_NOT_UPLOADED"));

        assertTrue(voucherRepository.findByVoucherId(voucher).isEmpty());
    }

    @Test
    void aResentVoucherIsAnsweredWithTheOriginalAndAnnouncedOnlyOnce() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);
        var voucher = UUID.randomUUID();
        uploadFile(agent, voucher, reservation, "image/jpeg", 2048);
        var first = register(agent, validBody(voucher, reservation, "image/jpeg", 2048))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        register(agent, body(voucher, reservation, "image/jpeg", 2048, "999", "USD", yesterday(), "OTHER", null,
                true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("DUPLICATE"))
                .andExpect(jsonPath("$.status").value("SYNCED"))
                .andExpect(jsonPath("$.receivedAt").value(JsonPath.<String>read(first, "$.receivedAt")));

        var stored = voucherRepository.findByVoucherId(voucher).orElseThrow();
        assertEquals(new BigDecimal("1500.00"), stored.getExtractedData().amount());
        assertFalse(stored.isManuallyCorrected());
        assertEquals(1, events.stream(PaymentVoucherReceivedEvent.class).count());
    }

    @Test
    void aRegisteredVoucherCannotGetANewUploadUrl() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);
        var voucher = UUID.randomUUID();
        uploadFile(agent, voucher, reservation, "image/jpeg", 2048);
        register(agent, validBody(voucher, reservation, "image/jpeg", 2048)).andExpect(status().isCreated());

        requestUrl(agent, voucher, reservation, "image/jpeg", 2048)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_CONFLICT"));
    }

    @Test
    void aVoucherIdCannotBeReusedForAnotherReservation() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);
        var otherReservation = operationOf(agent);
        var voucher = UUID.randomUUID();
        uploadFile(agent, voucher, reservation, "image/jpeg", 2048);
        register(agent, validBody(voucher, reservation, "image/jpeg", 2048)).andExpect(status().isCreated());

        register(agent, validBody(voucher, otherReservation, "image/jpeg", 2048))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_CONFLICT"));
    }

    @Test
    void invalidDataIsRejectedBeforeLookingForTheFile() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);

        register(agent, body(UUID.randomUUID(), reservation, "image/jpeg", 2048, "0", "PEN", yesterday(), "OP-1",
                null, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(containsString("amount")));
        register(agent, body(UUID.randomUUID(), reservation, "image/jpeg", 2048, "1500", "PEN",
                LocalDate.now(LIMA).plusDays(2), "OP-1", null, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(containsString("operationDate")));
        register(agent, body(UUID.randomUUID(), reservation, "image/jpeg", 2048, "1500", "S/", yesterday(), "OP-1",
                null, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("currency")));
        register(agent, body(UUID.randomUUID(), reservation, "image/jpeg", 2048, "1500", "PEN", yesterday(), " ",
                null, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("operationCode")));
        register(agent, body(UUID.randomUUID(), reservation, "image/jpeg", 2048, "1500", "PEN", yesterday(), "OP-1",
                "1.5", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("confidence")));
        register(agent, """
                {"voucherId": "%s", "reservationId": "%s", "contentType": "image/jpeg", "sizeBytes": 2048,
                 "amount": 1500, "currency": "PEN", "operationDate": "%s", "operationCode": "OP-1"}"""
                .formatted(UUID.randomUUID(), reservation, yesterday()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("manuallyCorrected")));
        assertEquals(0, events.stream(PaymentVoucherReceivedEvent.class).count());
    }

    @Test
    void onlyTheAgentOfTheReservationRegistersItsVoucher() throws Exception {
        var agent = AGENTS.incrementAndGet();
        var otherAgent = AGENTS.incrementAndGet();
        var reservation = operationOf(agent);
        var voucher = UUID.randomUUID();
        uploadFile(agent, voucher, reservation, "image/jpeg", 2048);
        var body = validBody(voucher, reservation, "image/jpeg", 2048);

        register(otherAgent, body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_OPERATION_NOT_FOUND"));
        perform("/api/v1/vouchers", bearer(Role.CATALOG_ADMIN, agent), body).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/vouchers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertTrue(voucherRepository.findByVoucherId(voucher).isEmpty());
    }

    /**
     * A reservation the agent synchronized and that took its own lot. The real synchronization leaves both the
     * reservation in financial, which receives the payment evidence, and the operation the voucher belongs to.
     */
    private UUID operationOf(long agent) throws Exception {
        var reservation = UUID.randomUUID();
        var prospect = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/field-sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FIELD_AGENT, agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prospects": [{"id": "%s", "document": "12345678", "fullName": "Ana Quispe"}],
                                 "reservations": [{"id": "%s", "lotId": %d, "prospectId": "%1$s",
                                                   "initialAmount": 1500, "reservedAt": "%s"}]}"""
                                .formatted(prospect, reservation, availableLot(), Instant.now().minusSeconds(3600))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations[0].result").value("SYNCED"));
        return reservation;
    }

    private Long availableLot() {
        var rules = new FinancingRules(BigDecimal.TEN, BigDecimal.TEN, 60, BigDecimal.ONE);
        var project = projectRepository.save(Project.create("Comprobantes", "Chilca", null, null, rules));
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        var lot = Lot.register(project.getId(), "V-01", new LotDimensions(new BigDecimal("120"), null, null),
                Money.of(new BigDecimal("45000")), boundary);
        return lotRepository.saveAll(List.of(lot)).getFirst().getId();
    }

    /** Asks for the URL and uploads a file of that type and size with it, as the app does. */
    private String uploadFile(long agent, UUID voucher, UUID reservation, String contentType, int sizeBytes)
            throws Exception {
        var answer = requestUrl(agent, voucher, reservation, contentType, sizeBytes)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var request = HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[sizeBytes]))
                .build();
        var upload = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, upload.statusCode(), upload.body());
        return JsonPath.read(answer, "$.objectKey");
    }

    private ResultActions requestUrl(long agent, UUID voucher, UUID reservation, String contentType, long sizeBytes)
            throws Exception {
        return perform("/api/v1/vouchers/upload-url", bearer(Role.FIELD_AGENT, agent), """
                {"voucherId": "%s", "reservationId": "%s", "contentType": "%s", "sizeBytes": %d}"""
                .formatted(voucher, reservation, contentType, sizeBytes));
    }

    private ResultActions register(long agent, String body) throws Exception {
        return perform("/api/v1/vouchers", bearer(Role.FIELD_AGENT, agent), body);
    }

    private ResultActions perform(String path, String authorization, String body) throws Exception {
        return mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String validBody(UUID voucher, UUID reservation, String contentType, long sizeBytes) {
        return body(voucher, reservation, contentType, sizeBytes, "1500.00", "PEN", yesterday(), "00123456", "0.97",
                false);
    }

    private static String body(UUID voucher, UUID reservation, String contentType, long sizeBytes, String amount,
                               String currency, LocalDate operationDate, String operationCode,
                               String ocrConfidence, boolean manuallyCorrected) {
        return """
                {"voucherId": "%s", "reservationId": "%s", "contentType": "%s", "sizeBytes": %d,
                 "amount": %s, "currency": "%s", "operationDate": "%s", "operationCode": "%s",
                 "ocrConfidence": %s, "manuallyCorrected": %s}"""
                .formatted(voucher, reservation, contentType, sizeBytes, amount, currency, operationDate,
                        operationCode, ocrConfidence, manuallyCorrected);
    }

    private static LocalDate yesterday() {
        return LocalDate.now(LIMA).minusDays(1);
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
