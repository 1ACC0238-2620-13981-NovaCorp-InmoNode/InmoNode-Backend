package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import org.jspecify.annotations.Nullable;

/**
 * The payment of an installment the buyer started (US-55), with what the app needs to show the gateway's form.
 *
 * @param clientSecret   the secret the app confirms the charge with; absent once it can no longer be paid
 * @param publishableKey the gateway's public key for the app
 * @param created        false when an earlier start already opened it (Scenario 3)
 */
public record InstallmentPaymentStart(InstallmentPaymentIntent intent, @Nullable String clientSecret,
                                      @Nullable String publishableKey, boolean created) {
}
