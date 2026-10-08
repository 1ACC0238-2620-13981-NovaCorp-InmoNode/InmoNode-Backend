package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

/**
 * The back office rejects a payment evidence (2.6.4: RejectPaymentCommand; US-25, Scenario 2).
 *
 * @param reason why, shown to the requester so they can send a substitute
 */
public record RejectPaymentEvidenceCommand(Long evidenceId, String reason) {
}
