package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature.docuseal;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureInvitation;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProvider;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProviderException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSheet;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSubmission;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DocuSeal's REST API. The open-source edition cannot create a submission from a PDF, so the contract is signed
 * through a template uploaded once: a signature sheet whose read-only fields the backend fills for each contract.
 * <p>
 * Before creating a submission it looks for a live one with the contract's reference, so an attempt that timed out
 * after DocuSeal created it is not repeated. DocuSeal writes its own host in the links it answers; the backend may
 * reach it through another, so the download is rewritten to {@code baseUrl}.
 */
public class DocuSealSignatureProvider implements SignatureProvider {

    static final String CONTRACT_FIELD = "Contrato";
    static final String BUYER_FIELD = "Comprador";
    static final String LOT_FIELD = "Lote";
    static final String ISSUED_ON_FIELD = "Emitido";
    static final String HASH_FIELD = "SHA-256";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_REDIRECTS = 3;

    private final RestClient client;
    private final URI baseUrl;
    private final String publicUrl;
    private final long templateId;
    private final String signerRole;

    /**
     * @param builder    a builder with the timeouts; the base URL and the token are set here
     * @param baseUrl    where the backend reaches DocuSeal
     * @param publicUrl  where the buyer reaches it, for the signing link
     * @param signerRole the role of the signer in the template
     */
    public DocuSealSignatureProvider(RestClient.Builder builder, String baseUrl, String publicUrl, String apiToken,
                                     long templateId, String signerRole) {
        this.baseUrl = URI.create(stripTrailingSlash(baseUrl));
        this.publicUrl = stripTrailingSlash(publicUrl.isBlank() ? baseUrl : publicUrl);
        this.templateId = templateId;
        this.signerRole = signerRole;
        this.client = builder.baseUrl(this.baseUrl.toString())
                .defaultHeader("X-Auth-Token", apiToken)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public SignatureInvitation requestSignature(SignatureSheet sheet) {
        var existing = liveSubmitterOf(sheet.externalId());
        if (existing != null) {
            return invitationOf(existing);
        }
        var values = new LinkedHashMap<String, String>();
        values.put(CONTRACT_FIELD, sheet.contract());
        values.put(BUYER_FIELD, sheet.buyer());
        values.put(LOT_FIELD, sheet.lot());
        values.put(ISSUED_ON_FIELD, sheet.issuedOn());
        values.put(HASH_FIELD, sheet.documentSha256());
        var submitter = new LinkedHashMap<String, Object>();
        submitter.put("role", signerRole);
        submitter.put("email", sheet.signerEmail());
        submitter.put("external_id", sheet.externalId());
        submitter.put("values", values);
        submitter.put("metadata", Map.of("app", "inmonode", "contractId", sheet.contractId()));
        var body = Map.of("template_id", templateId, "send_email", false, "submitters", List.of(submitter));
        var created = call("create the submission", () -> client.post().uri("/api/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(JSON.writeValueAsString(body))
                .retrieve().body(String.class));
        var first = created.isArray() ? created.path(0) : created.path("submitters").path(0);
        if (first.isMissingNode()) {
            throw new SignatureProviderException("DocuSeal created no submitter");
        }
        return invitationOf(first);
    }

    @Override
    public SignatureSubmission fetchSubmission(long submissionId) {
        var submission = call("read submission " + submissionId,
                () -> client.get().uri("/api/submissions/{id}", submissionId).retrieve().body(String.class));
        var signer = submission.path("submitters").path(0);
        var documents = submission.path("documents");
        var documentUrl = documents.isArray() && !documents.isEmpty() ? text(documents.path(0), "url") : null;
        return new SignatureSubmission(submission.path("id").asLong(submissionId), statusOf(submission, signer),
                text(signer, "external_id"), instant(text(submission, "completed_at")),
                text(signer, "decline_reason"), documentUrl);
    }

    @Override
    public byte[] downloadSignedDocument(SignatureSubmission submission) {
        if (submission.documentUrl() == null) {
            throw new SignatureProviderException("submission %d has no signed document".formatted(submission.id()));
        }
        var announced = URI.create(submission.documentUrl());
        var url = reachable(announced, announced.getAuthority());
        try {
            for (var hop = 0; hop <= MAX_REDIRECTS; hop++) {
                var current = url;
                var answer = client.get().uri(current)
                        .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                        .exchange((request, response) -> {
                            var status = response.getStatusCode();
                            if (status.is3xxRedirection() && response.getHeaders().getLocation() != null) {
                                return new Download(null, current.resolve(response.getHeaders().getLocation()));
                            }
                            if (!status.is2xxSuccessful()) {
                                throw new SignatureProviderException("DocuSeal answered HTTP %d to the signed document"
                                        .formatted(status.value()));
                            }
                            return new Download(response.getBody().readAllBytes(), null);
                        });
                if (answer.content() != null) {
                    if (answer.content().length == 0) {
                        throw new SignatureProviderException("DocuSeal answered an empty signed document");
                    }
                    return answer.content();
                }
                url = reachable(answer.redirect(), announced.getAuthority());
            }
            throw new SignatureProviderException("the signed document redirects too many times");
        } catch (RestClientException failure) {
            throw new SignatureProviderException("could not download the signed document of submission %d: %s"
                    .formatted(submission.id(), describe(failure)), failure);
        }
    }

    private record Download(byte[] content, URI redirect) {
    }

    /** A submitter of the contract that can still sign, or has signed; declined and expired ones are skipped. */
    private JsonNode liveSubmitterOf(String externalId) {
        var found = call("look for the submission of " + externalId, () -> client.get()
                .uri(builder -> builder.path("/api/submitters").queryParam("external_id", externalId).build())
                .retrieve().body(String.class));
        var submitters = found.isArray() ? found : found.path("data");
        for (var submitter : submitters) {
            var status = submitter.path("status").asString("");
            var declined = !submitter.path("declined_at").isNull() && !submitter.path("declined_at").isMissingNode();
            if (externalId.equals(text(submitter, "external_id")) && !declined
                    && !status.equals("declined") && !status.equals("expired")) {
                return submitter;
            }
        }
        return null;
    }

    private SignatureInvitation invitationOf(JsonNode submitter) {
        var submissionId = submitter.path("submission_id").asLong(0);
        var slug = text(submitter, "slug");
        if (submissionId <= 0 || slug == null) {
            throw new SignatureProviderException("DocuSeal answered a submitter without its submission or link");
        }
        return new SignatureInvitation(submissionId, publicUrl + "/s/" + slug);
    }

    private static SignatureSubmission.Status statusOf(JsonNode submission, JsonNode signer) {
        var status = text(submission, "status");
        if (status == null) {
            status = text(signer, "status");
        }
        return switch (status == null ? "" : status) {
            case "completed" -> SignatureSubmission.Status.COMPLETED;
            case "declined" -> SignatureSubmission.Status.DECLINED;
            case "expired" -> SignatureSubmission.Status.EXPIRED;
            default -> SignatureSubmission.Status.PENDING;
        };
    }

    /**
     * A link on DocuSeal's own host ({@code announcedAuthority}), moved to the host the backend reaches it through.
     * Links elsewhere, such as an external file storage, are left as they are.
     */
    private URI reachable(URI link, String announcedAuthority) {
        if (link.getAuthority() != null && !link.getAuthority().equalsIgnoreCase(announcedAuthority)) {
            return link;
        }
        return UriComponentsBuilder.fromUri(link)
                .scheme(baseUrl.getScheme()).host(baseUrl.getHost()).port(baseUrl.getPort())
                .build(true).toUri();
    }

    private JsonNode call(String what, java.util.function.Supplier<String> request) {
        try {
            var body = request.get();
            return JSON.readTree(body == null ? "null" : body);
        } catch (RestClientException failure) {
            throw new SignatureProviderException("DocuSeal could not %s: %s".formatted(what, describe(failure)), failure);
        } catch (JacksonException failure) {
            throw new SignatureProviderException("DocuSeal answered something that is not JSON to " + what, failure);
        }
    }

    private static String describe(RestClientException failure) {
        if (failure instanceof RestClientResponseException response) {
            return "HTTP " + response.getStatusCode().value();
        }
        return failure.getClass().getSimpleName();
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String stripTrailingSlash(String url) {
        var stripped = url.strip();
        return stripped.endsWith("/") ? stripped.substring(0, stripped.length() - 1) : stripped;
    }
}
