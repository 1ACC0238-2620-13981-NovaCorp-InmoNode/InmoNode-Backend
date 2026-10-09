package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ApprovePaymentEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RejectPaymentEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.VerificationOutcome;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Command side of the financial verification (2.6.4): the back office decides on each payment evidence.
 */
public interface VerificationCommandService {

    /**
     * Approves an evidence: the reservation is {@code VERIFIED} and the lot {@code RESERVED}. Fails with
     * {@code PAYMENT_EVIDENCE_NOT_FOUND}, {@code PAYMENT_EVIDENCE_CONFLICT} when it was already decided, and
     * {@code BUSINESS_RULE_VIOLATION} when {@link FinancialVerificationService} finds an obstacle (late, amount under
     * the down payment, another currency).
     */
    Result<VerificationOutcome, ApplicationError> handle(ApprovePaymentEvidenceCommand command);

    /**
     * Rejects an evidence with a reason. When it was the only one under review, the reservation waits for a substitute
     * again and its lot is held for a new window of its channel. Fails like the approval, except for the rules.
     *
     * @throws IllegalArgumentException when the reason is missing or too long
     */
    Result<VerificationOutcome, ApplicationError> handle(RejectPaymentEvidenceCommand command);
}
