package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentStart;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventRecord;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.InstallmentPaymentIntentResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.PaymentGatewayEventResource;
import org.jspecify.annotations.Nullable;

public final class InstallmentPaymentIntentResourceAssembler {

    private InstallmentPaymentIntentResourceAssembler() {}

    public static InstallmentPaymentIntentResource toResourceFromStart(InstallmentPaymentStart start) {
        return toResource(start.intent(), start.clientSecret(), start.publishableKey());
    }

    public static InstallmentPaymentIntentResource toResourceFromIntent(InstallmentPaymentIntent intent) {
        return toResource(intent, null, null);
    }

    public static PaymentGatewayEventResource toResourceFromEvent(PaymentGatewayEventRecord event) {
        return new PaymentGatewayEventResource(event.eventId(), event.outcome().name());
    }

    private static InstallmentPaymentIntentResource toResource(InstallmentPaymentIntent intent,
                                                               @Nullable String clientSecret,
                                                               @Nullable String publishableKey) {
        // Only a decline is worth telling the buyer; the other reasons are for the back office.
        var failureReason = intent.isOpen() ? intent.getLastFailure() : null;
        return new InstallmentPaymentIntentResource(intent.getId(), intent.getAccountStatementId(),
                intent.getInstallmentNumber(), intent.getStatus().name(), intent.getAmount(), intent.getCurrency(),
                intent.getCreatedAt(), intent.getExpiresAt(), intent.getConfirmedAt(), failureReason, clientSecret,
                publishableKey);
    }
}
