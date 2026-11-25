package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature.docuseal;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProvider;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureWebhookVerifier;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature.DisabledSignatureProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * DocuSeal signs the contracts when its URL, API token and template are configured; without them the app still
 * runs and the requests to sign wait. Its callbacks are trusted only with their webhook secret. The token and the
 * secret come from the environment and are never logged.
 */
@Configuration
public class DocuSealConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DocuSealConfiguration.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public SignatureProvider signatureProvider(@Value("${financial.signature.docuseal.url:}") String url,
                                               @Value("${financial.signature.docuseal.public-url:}") String publicUrl,
                                               @Value("${financial.signature.docuseal.api-token:}") String apiToken,
                                               @Value("${financial.signature.docuseal.template-id:}") String templateId,
                                               @Value("${financial.signature.docuseal.signer-role:Comprador}") String signerRole) {
        if (url.isBlank() || apiToken.isBlank() || templateId.isBlank()) {
            LOG.warn("DocuSeal is not configured (DOCUSEAL_URL, DOCUSEAL_API_TOKEN, DOCUSEAL_TEMPLATE_ID): "
                    + "issued contracts wait to be sent to signature");
            return new DisabledSignatureProvider();
        }
        long template;
        try {
            template = Long.parseLong(templateId.strip());
        } catch (NumberFormatException e) {
            LOG.error("DOCUSEAL_TEMPLATE_ID must be the number of the signature sheet template; contracts wait");
            return new DisabledSignatureProvider();
        }
        var http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(READ_TIMEOUT);
        LOG.info("Contracts are signed through DocuSeal with the signature sheet template {}", template);
        return new DocuSealSignatureProvider(RestClient.builder().requestFactory(requests), url, publicUrl, apiToken,
                template, signerRole);
    }

    @Bean
    public SignatureWebhookVerifier signatureWebhookVerifier(
            @Value("${financial.signature.docuseal.webhook-secret:}") String secret, Clock clock) {
        if (secret.isBlank()) {
            LOG.warn("No DocuSeal webhook secret (DOCUSEAL_WEBHOOK_SECRET): no signature can be confirmed");
        }
        return new DocuSealWebhookVerifier(secret, clock);
    }
}
