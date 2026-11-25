package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

/**
 * The back office sends a contract to signature again (US-56, Scenario 2): after its attempts ran out, after the
 * buyer declined, or for a contract issued before the signature existed.
 */
public record RequestContractSignatureCommand(Long contractId) {

    public RequestContractSignatureCommand {
        if (contractId == null || contractId < 1) {
            throw new IllegalArgumentException("contractId must be positive");
        }
    }
}
