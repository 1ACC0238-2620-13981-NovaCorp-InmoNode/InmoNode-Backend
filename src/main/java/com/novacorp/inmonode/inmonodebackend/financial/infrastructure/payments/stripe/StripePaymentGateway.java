package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.GatewayPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.GatewayPaymentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGateway;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayException;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Charges installments with Stripe PaymentIntents. It never passes the payment method types, so Stripe offers the
 * ones enabled in its dashboard. Each PaymentIntent carries in its metadata {@link PaymentGateway#APP} and the id of
 * its intent, and is created with the idempotency key {@code inmonode-intent-<id>}: opening the same intent again
 * returns the same PaymentIntent instead of a second charge.
 */
public class StripePaymentGateway implements PaymentGateway {

    /** Metadata keys of every PaymentIntent of InmoNode. */
    public static final String APP_KEY = "app";
    public static final String INTENT_KEY = "installmentPaymentIntentId";
    public static final String STATEMENT_KEY = "accountStatementId";
    public static final String INSTALLMENT_KEY = "installmentNumber";

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final int NETWORK_RETRIES = 2;

    private final StripeClient client;
    private final @Nullable String publishableKey;

    public StripePaymentGateway(String secretKey, @Nullable String publishableKey) {
        this.client = StripeClient.builder()
                .setApiKey(secretKey)
                .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                .setReadTimeout(READ_TIMEOUT_MILLIS)
                .setMaxNetworkRetries(NETWORK_RETRIES)
                .build();
        this.publishableKey = publishableKey == null || publishableKey.isBlank() ? null : publishableKey;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public @Nullable String publishableKey() {
        return publishableKey;
    }

    @Override
    public GatewayPaymentIntent open(InstallmentPaymentIntent intent) {
        var params = PaymentIntentCreateParams.builder()
                .setAmount(intent.amountInMinorUnits())
                .setCurrency(intent.getCurrency().toLowerCase(Locale.ROOT))
                .setDescription("InmoNode - cuota %d".formatted(intent.getInstallmentNumber()))
                .putAllMetadata(Map.of(
                        APP_KEY, APP,
                        INTENT_KEY, intent.getId().toString(),
                        STATEMENT_KEY, intent.getAccountStatementId().toString(),
                        INSTALLMENT_KEY, Integer.toString(intent.getInstallmentNumber())))
                .build();
        var options = RequestOptions.builder().setIdempotencyKey("inmonode-intent-" + intent.getId()).build();
        try {
            return toGateway(client.v1().paymentIntents().create(params, options));
        } catch (StripeException ex) {
            throw failure("open the payment " + intent.getId(), ex);
        }
    }

    @Override
    public GatewayPaymentIntent retrieve(String providerId) {
        try {
            return toGateway(client.v1().paymentIntents().retrieve(providerId));
        } catch (StripeException ex) {
            throw failure("read the payment " + providerId, ex);
        }
    }

    @Override
    public GatewayPaymentIntent cancel(String providerId) {
        try {
            return toGateway(client.v1().paymentIntents().cancel(providerId));
        } catch (StripeException ex) {
            throw failure("cancel the payment " + providerId, ex);
        }
    }

    static GatewayPaymentIntent toGateway(PaymentIntent paymentIntent) {
        var error = paymentIntent.getLastPaymentError();
        return new GatewayPaymentIntent(paymentIntent.getId(), status(paymentIntent.getStatus()),
                orZero(paymentIntent.getAmount()), orZero(paymentIntent.getAmountReceived()),
                paymentIntent.getCurrency(), paymentIntent.getLatestCharge(), paymentIntent.getClientSecret(),
                error == null ? null : error.getMessage(), paymentIntent.getMetadata());
    }

    static GatewayPaymentStatus status(@Nullable String status) {
        return switch (status == null ? "" : status) {
            case "succeeded" -> GatewayPaymentStatus.SUCCEEDED;
            case "canceled" -> GatewayPaymentStatus.CANCELED;
            case "processing" -> GatewayPaymentStatus.PROCESSING;
            default -> GatewayPaymentStatus.PENDING;
        };
    }

    private static long orZero(@Nullable Long amount) {
        return amount == null ? 0 : amount;
    }

    private static PaymentGatewayException failure(String operation, StripeException ex) {
        return new PaymentGatewayException("Stripe could not %s: %s".formatted(operation, ex.getMessage()), ex);
    }
}
