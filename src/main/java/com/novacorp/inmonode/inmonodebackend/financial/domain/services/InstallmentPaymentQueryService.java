package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetInstallmentPaymentIntentQuery;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * US-55: how the payment of an installment went.
 */
public interface InstallmentPaymentQueryService {

    /** Fails with {@code PAYMENT_INTENT_NOT_FOUND} when it is not of that installment of the buyer. */
    Result<InstallmentPaymentIntent, ApplicationError> handle(GetInstallmentPaymentIntentQuery query);
}
