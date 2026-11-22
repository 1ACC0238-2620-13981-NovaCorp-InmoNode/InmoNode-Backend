package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectImageRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The back office uploads the photos and renders of a project (US-57) and registers them in its gallery, against a
 * real PostgreSQL and a real S3-compatible storage. Each test uses its own project.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class ProjectImageUploadIntegrationTest {

    private static final long ADMIN = 7_001;
    private static final int FIVE_MB = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectImageRepository projectImageRepository;

    @Autowired
    private ObjectStorage objectStorage;

    @Test
    void theCatalogAdminUploadsAndRegistersAnImageOfADraftProject() throws Exception {
        var project = project();
        var image = UUID.randomUUID();
        var before = Instant.now();

        var answer = call(project, "/upload-url", image, "image/jpeg", 2048)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("projects/%d/images/%s.jpg".formatted(project, image)))
                .andExpect(jsonPath("$.headers['Content-Type']").value("image/jpeg"))
                .andExpect(jsonPath("$.headers['Content-Length']").value("2048"))
                .andReturn().getResponse().getContentAsString();
        var expiresAt = Instant.parse(JsonPath.read(answer, "$.expiresAt"));
        assertTrue(expiresAt.isAfter(before.plus(Duration.ofMinutes(9))), "valid for 10 minutes");
        assertTrue(expiresAt.isBefore(before.plus(Duration.ofMinutes(11))), "valid for 10 minutes");
        var upload = upload(answer, new byte[2048]);
        assertEquals(200, upload.statusCode(), upload.body());

        var registered = register(project, image, "image/jpeg", 2048)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(project))
                .andExpect(jsonPath("$.imageId").value(image.toString()))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.sizeBytes").value(2048))
                .andExpect(jsonPath("$.result").value("RECEIVED"))
                .andReturn().getResponse().getContentAsString();

        var gallery = projectImageRepository.findByProjectId(project);
        assertEquals(1, gallery.size());
        assertEquals(image, gallery.getFirst().image().imageId());
        register(project, image, "image/jpeg", 2048)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("DUPLICATE"))
                .andExpect(jsonPath("$.registeredAt").value(JsonPath.<String>read(registered, "$.registeredAt")));
        assertEquals(1, projectImageRepository.findByProjectId(project).size());
    }

    @Test
    void theImagesOfAProjectKeepTheOrderInWhichTheyWereRegistered() throws Exception {
        var project = project();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        uploadAndRegister(project, first, "image/png");
        uploadAndRegister(project, second, "image/jpeg");

        var gallery = projectImageRepository.findByProjectId(project);
        assertEquals(2, gallery.size());
        assertEquals(first, gallery.get(0).image().imageId());
        assertEquals(second, gallery.get(1).image().imageId());
        assertTrue(projectImageRepository.findByProjectId(project()).isEmpty(), "another project has no images");
    }

    @Test
    void aRegisteredImageCannotBeReplaced() throws Exception {
        var project = project();
        var image = UUID.randomUUID();
        uploadAndRegister(project, image, "image/jpeg");

        call(project, "/upload-url", image, "image/jpeg", 2048)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_IMAGE_CONFLICT"));
        register(project, image, "image/jpeg", 4096)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_IMAGE_CONFLICT"));
        register(project(), image, "image/jpeg", 2048)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_IMAGE_CONFLICT"));
        assertEquals(2048, projectImageRepository.findByImageId(image).orElseThrow().image().sizeBytes());
    }

    @Test
    void anImageThatIsNotInTheRepositoryOrDiffersFromTheDeclaredOneIsNotRegistered() throws Exception {
        var project = project();
        var image = UUID.randomUUID();

        register(project, image, "image/jpeg", 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_IMAGE_FILE_NOT_UPLOADED"));
        var answer = call(project, "/upload-url", image, "image/jpeg", 2048)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(200, upload(answer, new byte[2048]).statusCode());
        register(project, image, "image/jpeg", 4096)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_IMAGE_FILE_NOT_UPLOADED"));
        register(project, image, "image/png", 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_IMAGE_FILE_NOT_UPLOADED"));

        assertTrue(projectImageRepository.findByProjectId(project).isEmpty());
        register(project, image, "image/jpeg", 2048).andExpect(status().isCreated());
        assertTrue(objectStorage.describe(JsonPath.read(answer, "$.objectKey")).isPresent());
    }

    @Test
    void onlyJpegOrPngImagesUpToFiveMegabytesAreAccepted() throws Exception {
        var project = project();
        var image = UUID.randomUUID();

        call(project, "/upload-url", image, " Image/PNG", FIVE_MB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("projects/%d/images/%s.png".formatted(project, image)))
                .andExpect(jsonPath("$.headers['Content-Type']").value("image/png"));
        call(project, "/upload-url", image, "application/pdf", 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(containsString("contentType")));
        call(project, "/upload-url", image, "image/jpeg", FIVE_MB + 1)
                .andExpect(status().isBadRequest());
        call(project, "/upload-url", image, "image/jpeg", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("sizeBytes")));
        perform(bearer(Role.CATALOG_ADMIN), project, "/upload-url", """
                {"contentType": "image/jpeg", "sizeBytes": 2048}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("imageId")));
    }

    @Test
    void anUnknownProjectAnswersNotFound() throws Exception {
        var unknown = Long.MAX_VALUE;

        call(unknown, "/upload-url", UUID.randomUUID(), "image/jpeg", 2048)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        register(unknown, UUID.randomUUID(), "image/jpeg", 2048)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void onlyTheCatalogAdminUploadsProjectImages() throws Exception {
        var project = project();
        var body = body(UUID.randomUUID(), "image/jpeg", 2048);

        perform(bearer(Role.FIELD_AGENT), project, "/upload-url", body)
                .andExpect(status().isForbidden());
        perform(bearer(Role.FINANCE_ADMIN), project, "", body)
                .andExpect(status().isForbidden());
        perform(bearer(Role.BUYER), project, "", body)
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path(project, "/upload-url")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private void uploadAndRegister(long project, UUID image, String contentType) throws Exception {
        var answer = call(project, "/upload-url", image, contentType, 2048)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(200, upload(answer, new byte[2048]).statusCode());
        register(project, image, contentType, 2048).andExpect(status().isCreated());
    }

    private ResultActions register(long project, UUID image, String contentType, long sizeBytes) throws Exception {
        return call(project, "", image, contentType, sizeBytes);
    }

    private ResultActions call(long project, String suffix, UUID image, String contentType, long sizeBytes)
            throws Exception {
        return perform(bearer(Role.CATALOG_ADMIN), project, suffix, body(image, contentType, sizeBytes));
    }

    private ResultActions perform(String authorization, long project, String suffix, String body) throws Exception {
        return mockMvc.perform(post(path(project, suffix))
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String path(long project, String suffix) {
        return "/api/v1/projects/%d/images%s".formatted(project, suffix);
    }

    private static String body(UUID image, String contentType, long sizeBytes) {
        return """
                {"imageId": "%s", "contentType": "%s", "sizeBytes": %d}""".formatted(image, contentType, sizeBytes);
    }

    /** The PUT a client makes with the answer; Content-Length comes from the body, as in any HTTP client. */
    private HttpResponse<String> upload(String answer, byte[] file) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                        .header("Content-Type", JsonPath.<String>read(answer, "$.headers['Content-Type']"))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(file))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** A draft project: the images are usually loaded before it is published. */
    private long project() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        return projectRepository.save(Project.create("Galería", "Asia", null, null, rules)).getId();
    }

    private String bearer(Role role) {
        var user = User.restore(ADMIN, role.name().toLowerCase() + ADMIN + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
