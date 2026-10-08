package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;

/**
 * @param acknowledgedAt when the buyer first gave their preliminary agreement (US-22)
 */
public record ContractAcknowledgmentResource(Long contractId, Instant acknowledgedAt) {
}
