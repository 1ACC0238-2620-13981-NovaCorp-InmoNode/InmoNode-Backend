package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

/**
 * The calling buyer gives their preliminary agreement with the terms of the contract (US-22; 2.6.4:
 * RegisterBuyerAcknowledgmentCommand).
 */
public record AcknowledgeContractCommand(Long contractId) {
}
