package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import org.jspecify.annotations.Nullable;

/**
 * The back office approves a payment evidence received on time (2.6.4: VerifyPaymentCommand).
 *
 * @param note optional remark of the reviewer
 */
public record ApprovePaymentEvidenceCommand(Long evidenceId, @Nullable String note) {
}
