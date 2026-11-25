package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGateway;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayWebhookVerifier;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.DisabledPaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stripe charges installments when its secret key is configured; without it the app still runs and starting a
 * payment answers that payments are unavailable. Its webhooks are trusted only with their signing secret. The keys
 * come from the environment and are never logged.
 */
@Configuration
public class StripePaymentsConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(StripePaymentsConfiguration.class);

    @Bean
    public PaymentGateway paymentGateway(@Value("${financial.payments.stripe.secret-key:}") String secretKey,
                                         @Value("${financial.payments.stripe.publishable-key:}") String publishableKey) {
        if (secretKey.isBlank()) {
            LOG.warn("No Stripe secret key (STRIPE_SECRET_KEY): installments cannot be paid online");
            return new DisabledPaymentGateway();
        }
        if (publishableKey.isBlank()) {
            LOG.warn("No Stripe publishable key (STRIPE_PUBLISHABLE_KEY): the app cannot show the payment form");
        }
        LOG.info("Installments are charged through Stripe in {} mode", secretKey.contains("_live_") ? "live" : "test");
        return new StripePaymentGateway(secretKey, publishableKey);
    }

    @Bean
    public PaymentGatewayWebhookVerifier paymentGatewayWebhookVerifier(
            @Value("${financial.payments.stripe.webhook-secret:}") String signingSecret) {
        if (signingSecret.isBlank()) {
            LOG.warn("No Stripe webhook signing secret (STRIPE_WEBHOOK_SECRET): no payment can be confirmed");
        }
        return new StripeWebhookVerifier(signingSecret);
    }
}
