package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import org.jspecify.annotations.Nullable;

/**
 * A contract and where its request to sign is, as the back office sees it (US-56).
 *
 * @param delivery {@code null} for a contract that was never sent to signature
 */
public record ContractSignatureProgress(Contract contract, @Nullable ContractSignatureDelivery delivery) {
}
