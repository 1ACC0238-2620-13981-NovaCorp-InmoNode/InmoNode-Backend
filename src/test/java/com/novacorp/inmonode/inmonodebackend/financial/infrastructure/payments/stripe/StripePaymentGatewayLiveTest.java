package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.payments.stripe;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.GatewayPaymentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments.PaymentGatewayException;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentIntentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The real Stripe API in test mode: it runs only when STRIPE_SECRET_KEY holds a test key (sk_test_ or rk_test_), so
 * CI and anyone without one skip it. It opens a PaymentIntent of S/ 5.00, opens it again, reads it and cancels it.
 */
@EnabledIfEnvironmentVariable(named = "STRIPE_SECRET_KEY", matches = "(sk|rk)_test_.+")
class StripePaymentGatewayLiveTest {

    private final StripePaymentGateway gateway = new StripePaymentGateway(System.getenv("STRIPE_SECRET_KEY"),
            System.getenv("STRIPE_PUBLISHABLE_KEY"));

    @Test
    void aPaymentIsOpenedOnceTaggedAsInmoNodeAndCanBeCanceled() {
        var now = Instant.now();
        var intent = InstallmentPaymentIntent.restore(UUID.randomUUID(), 0L, 1, 0L, new BigDecimal("5.00"), "PEN",
                null, now, now.plus(Duration.ofMinutes(30)), InstallmentPaymentIntentStatus.OPEN, null, null, null,
                null);

        var opened = gateway.open(intent);
        var again = gateway.open(intent);

        assertTrue(opened.id().startsWith("pi_"));
        assertEquals(opened.id(), again.id(), "the idempotency key returns the same PaymentIntent");
        assertEquals(GatewayPaymentStatus.PENDING, opened.status());
        assertEquals(500, opened.amount());
        assertEquals("PEN", opened.currency());
        assertNotNull(opened.clientSecret());
        assertEquals("inmonode", opened.metadata().get(StripePaymentGateway.APP_KEY));
        assertEquals(intent.getId().toString(), opened.metadata().get(StripePaymentGateway.INTENT_KEY));

        var read = gateway.retrieve(opened.id());
        assertEquals(opened.clientSecret(), read.clientSecret());
        assertEquals(0, read.amountReceived());

        assertEquals(GatewayPaymentStatus.CANCELED, gateway.cancel(opened.id()).status());
        assertThrows(PaymentGatewayException.class, () -> gateway.cancel(opened.id()));
        assertThrows(PaymentGatewayException.class, () -> gateway.retrieve("pi_does_not_exist"));
    }
}
