package com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceSource;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proof that the down payment of a reservation was paid (2.6.4.1), waiting for the back office to verify it. It lives
 * inside its {@code Reservation} and is identified there by its {@code reference}.
 *
 * <p>A late evidence arrived when the reservation no longer held its lot (it expired or lost it in a conflict): it is
 * kept for the back office to review, but it does not move the reservation.</p>
 *
 * <p>The back office approves it or rejects it with a reason (US-25); the decision is final.</p>
 */
public class PaymentEvidence {

    public static final int MAX_NOTE_LENGTH = 500;

    private final @Nullable Long id;
    private final UUID reference;
    private final PaymentEvidenceSource source;
    private final Money amount;
    private final LocalDate operationDate;
    private final String operationCode;
    private final boolean manuallyCorrected;
    private final @Nullable String objectKey;
    private final PaymentEvidenceStatus status;
    private final boolean late;
    private final Instant submittedAt;
    private final @Nullable Long reviewerId;
    private final @Nullable String reviewerNote;
    private final @Nullable Instant reviewedAt;

    private PaymentEvidence(@Nullable Long id, UUID reference, PaymentEvidenceSource source, Money amount,
                            LocalDate operationDate, String operationCode, boolean manuallyCorrected,
                            @Nullable String objectKey, PaymentEvidenceStatus status, boolean late,
                            Instant submittedAt, @Nullable Long reviewerId, @Nullable String reviewerNote,
                            @Nullable Instant reviewedAt) {
        this.id = id;
        this.reference = reference;
        this.source = source;
        this.amount = amount;
        this.operationDate = operationDate;
        this.operationCode = operationCode;
        this.manuallyCorrected = manuallyCorrected;
        this.objectKey = objectKey;
        this.status = status;
        this.late = late;
        this.submittedAt = submittedAt;
        this.reviewerId = reviewerId;
        this.reviewerNote = reviewerNote;
        this.reviewedAt = reviewedAt;
    }

    /**
     * The voucher sent from the field app, with the data read from it.
     *
     * @param voucherId         id the device generated for the voucher; the reference of this evidence
     * @param manuallyCorrected whether the agent corrected the data the OCR read
     * @param objectKey         where the voucher file lives in the file repository
     * @param submittedAt       when the voucher reached the server
     */
    public static PaymentEvidence fromVoucher(UUID voucherId, Money amount, LocalDate operationDate,
                                              String operationCode, boolean manuallyCorrected, String objectKey,
                                              Instant submittedAt) {
        if (voucherId == null || amount == null || operationDate == null || operationCode == null
                || operationCode.isBlank() || objectKey == null || objectKey.isBlank() || submittedAt == null) {
            throw new IllegalArgumentException(
                    "a voucher evidence needs its id, amount, operation date and code, file and submission date");
        }
        return new PaymentEvidence(null, voucherId, PaymentEvidenceSource.VOUCHER, amount, operationDate,
                operationCode, manuallyCorrected, objectKey, PaymentEvidenceStatus.PENDING, false, submittedAt,
                null, null, null);
    }

    /** Rebuilds an already persisted evidence. */
    public static PaymentEvidence restore(Long id, UUID reference, PaymentEvidenceSource source, Money amount,
                                          LocalDate operationDate, String operationCode, boolean manuallyCorrected,
                                          @Nullable String objectKey, PaymentEvidenceStatus status, boolean late,
                                          Instant submittedAt, @Nullable Long reviewerId,
                                          @Nullable String reviewerNote, @Nullable Instant reviewedAt) {
        return new PaymentEvidence(id, reference, source, amount, operationDate, operationCode, manuallyCorrected,
                objectKey, status, late, submittedAt, reviewerId, reviewerNote, reviewedAt);
    }

    /** The same evidence, marked as arrived after its reservation stopped holding the lot. */
    public PaymentEvidence markedLate() {
        return new PaymentEvidence(id, reference, source, amount, operationDate, operationCode, manuallyCorrected,
                objectKey, status, true, submittedAt, reviewerId, reviewerNote, reviewedAt);
    }

    /**
     * The same evidence, approved by the back office.
     *
     * @param note optional remark of the reviewer
     * @throws IllegalStateException when it was already decided
     */
    public PaymentEvidence approved(Long reviewerId, @Nullable String note, Instant reviewedAt) {
        return decided(PaymentEvidenceStatus.APPROVED, reviewerId, normalizedNote(note), reviewedAt);
    }

    /**
     * The same evidence, rejected by the back office (US-25, Scenario 2).
     *
     * @param reason why it was rejected, shown to the requester (e.g. illegible, wrong account)
     * @throws IllegalArgumentException when the reason is missing or too long
     * @throws IllegalStateException    when it was already decided
     */
    public PaymentEvidence rejected(Long reviewerId, String reason, Instant reviewedAt) {
        var normalized = normalizedNote(reason);
        if (normalized == null) {
            throw new IllegalArgumentException("reason is required to reject a payment evidence");
        }
        return decided(PaymentEvidenceStatus.REJECTED, reviewerId, normalized, reviewedAt);
    }

    private PaymentEvidence decided(PaymentEvidenceStatus decision, Long reviewerId, @Nullable String note,
                                    Instant reviewedAt) {
        if (!isPending()) {
            throw new IllegalStateException("payment evidence %s is already %s".formatted(reference, status));
        }
        if (reviewerId == null || reviewedAt == null) {
            throw new IllegalArgumentException("a decision needs its reviewer and date");
        }
        return new PaymentEvidence(id, reference, source, amount, operationDate, operationCode, manuallyCorrected,
                objectKey, decision, late, submittedAt, reviewerId, note, reviewedAt);
    }

    private static @Nullable String normalizedNote(@Nullable String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        var stripped = note.strip();
        if (stripped.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("the reviewer note must have at most %d characters"
                    .formatted(MAX_NOTE_LENGTH));
        }
        return stripped;
    }

    public boolean isPending() {
        return status == PaymentEvidenceStatus.PENDING;
    }

    public @Nullable Long getId() { return id; }
    public UUID getReference() { return reference; }
    public PaymentEvidenceSource getSource() { return source; }
    public Money getAmount() { return amount; }
    public LocalDate getOperationDate() { return operationDate; }
    public String getOperationCode() { return operationCode; }
    public boolean isManuallyCorrected() { return manuallyCorrected; }
    public @Nullable String getObjectKey() { return objectKey; }
    public PaymentEvidenceStatus getStatus() { return status; }
    public boolean isLate() { return late; }
    public Instant getSubmittedAt() { return submittedAt; }
    public @Nullable Long getReviewerId() { return reviewerId; }
    public @Nullable String getReviewerNote() { return reviewerNote; }
    public @Nullable Instant getReviewedAt() { return reviewedAt; }
}
