package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignature;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.CoOwner;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignedContractDocument;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Preliminary purchase contract of a reservation (2.6.4.1, US-21), issued by the back office once the payment was
 * verified. It refers to its {@link Reservation} by id, one contract per reservation, and keeps the PDF in the file
 * repository. For now only web reservations get one: their buyer has an account to read it.
 * <p>
 * Once issued, it is sent to the signature provider (US-56). The buyer signs a signature sheet that cites the
 * contract and the SHA-256 of its PDF; the signature counts only when the provider confirms it.
 */
public class Contract {

    private static final int MAX_DECLINE_REASON_LENGTH = 500;

    private @Nullable CoOwner coOwner;
    private final @Nullable Long id;
    private final Long reservationId;
    private final UUID transactionId;
    private final Long buyerId;
    private final Long lotId;
    private final UUID documentId;
    private final String objectKey;
    private final long sizeBytes;
    private ContractStatus status;
    private final Instant issuedAt;
    private final Long issuedBy;
    private @Nullable Instant buyerAcknowledgedAt;
    private @Nullable String documentSha256;
    private @Nullable Long signatureSubmissionId;
    private @Nullable String signingUrl;
    private @Nullable Instant signatureRequestedAt;
    private @Nullable Instant signedAt;
    private @Nullable SignedContractDocument signedDocument;
    private @Nullable Instant signatureDeclinedAt;
    private @Nullable String signatureDeclineReason;

    private Contract(@Nullable Long id, Long reservationId, UUID transactionId, Long buyerId, Long lotId,
                     UUID documentId, String objectKey, long sizeBytes, ContractStatus status, Instant issuedAt,
                     Long issuedBy, @Nullable Instant buyerAcknowledgedAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.transactionId = transactionId;
        this.buyerId = buyerId;
        this.lotId = lotId;
        this.documentId = documentId;
        this.objectKey = objectKey;
        this.sizeBytes = sizeBytes;
        this.status = status;
        this.issuedAt = issuedAt;
        this.issuedBy = issuedBy;
        this.buyerAcknowledgedAt = buyerAcknowledgedAt;
    }

    /**
     * Why the reservation cannot get a contract yet, or ever in this version; empty when it can.
     */
    public static Optional<String> issuingObstacle(Reservation reservation) {
        if (reservation.getChannel() != ReservationChannel.WEB) {
            return Optional.of("only web reservations get a contract for now; this one was made in the field");
        }
        if (reservation.getStatus() != ReservationStatus.VERIFIED) {
            return Optional.of("the reservation is %s; its payment must be verified first"
                    .formatted(reservation.getStatus()));
        }
        return Optional.empty();
    }

    /**
     * The back office issues the contract with the PDF it uploaded.
     *
     * @throws IllegalStateException when {@link #issuingObstacle} finds an obstacle
     */
    public static Contract issue(Reservation reservation, ContractDocument document, Long issuedBy, Instant now) {
        issuingObstacle(reservation).ifPresent(obstacle -> {
            throw new IllegalStateException(obstacle);
        });
        if (reservation.getId() == null || reservation.getSourceEventId() == null
                || !reservation.getSourceEventId().equals(document.transactionId())) {
            throw new IllegalArgumentException("the document must belong to this saved reservation");
        }
        var contract = new Contract(null, reservation.getId(), reservation.getSourceEventId(), reservation.getRequesterId(),
                reservation.getLotId(), document.documentId(), document.objectKey(), document.sizeBytes(),
                ContractStatus.ISSUED, now, issuedBy, null);
        contract.coOwner = reservation.getCoOwner();
        return contract;
    }

    /**
     * Issues the contract and keeps the SHA-256 of its PDF, which the signature sheet will cite (US-56).
     *
     * @throws IllegalArgumentException when the hash is not a SHA-256 in lowercase hexadecimal
     */
    public static Contract issue(Reservation reservation, ContractDocument document, String documentSha256,
                                 Long issuedBy, Instant now) {
        var contract = issue(reservation, document, issuedBy, now);
        contract.attachDocumentSha256(documentSha256);
        return contract;
    }

    /**
     * US-22: the buyer gives their preliminary agreement with the terms. Only the first one counts, so repeating it
     * keeps the original date.
     *
     * @return whether it was registered now
     * @throws IllegalArgumentException when the user is not the buyer of the contract
     */
    public boolean registerBuyerAcknowledgment(Long userId, Instant now) {
        if (!belongsTo(userId)) {
            throw new IllegalArgumentException("only the buyer of the contract can acknowledge it");
        }
        if (buyerAcknowledgedAt != null) {
            return false;
        }
        buyerAcknowledgedAt = now;
        return true;
    }

    /**
     * Keeps the SHA-256 of the issued PDF. A contract issued before US-56 gets it when the back office first sends
     * it to signature; once kept it never changes.
     *
     * @throws IllegalArgumentException when it is not a SHA-256, or differs from the one already kept
     */
    public void attachDocumentSha256(String sha256) {
        if (!SignedContractDocument.isSha256(sha256)) {
            throw new IllegalArgumentException("documentSha256 must be 64 lowercase hexadecimal characters");
        }
        if (documentSha256 != null && !documentSha256.equals(sha256)) {
            throw new IllegalArgumentException("the PDF of the contract changed since it was issued");
        }
        documentSha256 = sha256;
    }

    /** Whether it can be sent to signature now: issued, or declined and sent again. */
    public boolean canBeSentToSignature() {
        return status == ContractStatus.ISSUED || status == ContractStatus.SIGNATURE_DECLINED;
    }

    /**
     * The provider has the contract and the buyer can sign it at {@code signingUrl}. Recording the same submission
     * again changes nothing.
     *
     * @return whether it changed now
     * @throws IllegalStateException when it cannot be sent to signature, or another submission is already open
     */
    public boolean requestSignature(long submissionId, String signingUrl, Instant at) {
        if (status == ContractStatus.SIGNATURE_REQUESTED && Objects.equals(signatureSubmissionId, submissionId)) {
            return false;
        }
        if (!canBeSentToSignature()) {
            throw new IllegalStateException("the contract is %s; it cannot be sent to signature".formatted(status));
        }
        if (documentSha256 == null) {
            throw new IllegalStateException("the signature sheet needs the SHA-256 of the issued PDF");
        }
        if (signingUrl == null || signingUrl.isBlank()) {
            throw new IllegalArgumentException("the buyer needs a link to sign");
        }
        if (status == ContractStatus.SIGNATURE_DECLINED && Objects.equals(signatureSubmissionId, submissionId)) {
            throw new IllegalStateException("submission %d was declined; a new one is needed".formatted(submissionId));
        }
        this.signatureSubmissionId = submissionId;
        this.signingUrl = signingUrl;
        this.signatureRequestedAt = at;
        this.signatureDeclinedAt = null;
        this.signatureDeclineReason = null;
        this.status = ContractStatus.SIGNATURE_REQUESTED;
        return true;
    }

    /**
     * US-56, Scenario 1: the provider confirmed the signature of its submission. The signed sheet is kept; a second
     * confirmation of the same submission changes nothing (Scenario 2).
     *
     * @return whether it was signed now
     * @throws IllegalStateException when the contract does not wait for that submission's signature
     */
    public boolean registerSignature(long submissionId, SignedContractDocument document, Instant at) {
        if (status == ContractStatus.SIGNED) {
            if (Objects.equals(signatureSubmissionId, submissionId)) {
                return false;
            }
            throw new IllegalStateException("the contract was signed in submission " + signatureSubmissionId);
        }
        requireAwaiting(submissionId);
        this.signedDocument = Objects.requireNonNull(document);
        this.signedAt = at;
        this.signingUrl = null;
        this.status = ContractStatus.SIGNED;
        return true;
    }

    /**
     * The buyer declined to sign the submission. The back office can send the contract again.
     *
     * @return whether it was declined now
     * @throws IllegalStateException when the contract does not wait for that submission's signature
     */
    public boolean declineSignature(long submissionId, @Nullable String reason, Instant at) {
        if (status == ContractStatus.SIGNATURE_DECLINED && Objects.equals(signatureSubmissionId, submissionId)) {
            return false;
        }
        requireAwaiting(submissionId);
        this.signatureDeclinedAt = at;
        this.signatureDeclineReason = reason == null || reason.isBlank() ? null
                : reason.strip().substring(0, Math.min(reason.strip().length(), MAX_DECLINE_REASON_LENGTH));
        this.signingUrl = null;
        this.status = ContractStatus.SIGNATURE_DECLINED;
        return true;
    }

    public boolean isSigned() {
        return status == ContractStatus.SIGNED;
    }

    public boolean isAwaitingSignature() {
        return status == ContractStatus.SIGNATURE_REQUESTED;
    }

    public boolean belongsTo(Long userId) {
        return buyerId.equals(userId);
    }

    private void requireAwaiting(long submissionId) {
        if (status != ContractStatus.SIGNATURE_REQUESTED) {
            throw new IllegalStateException("the contract is %s, not waiting for a signature".formatted(status));
        }
        if (!Objects.equals(signatureSubmissionId, submissionId)) {
            throw new IllegalStateException("the contract waits for submission %d, not %d"
                    .formatted(signatureSubmissionId, submissionId));
        }
    }

    /** Rebuilds an already persisted contract. */
    public static Contract restore(Long id, Long reservationId, UUID transactionId, Long buyerId, Long lotId,
                                   UUID documentId, String objectKey, long sizeBytes, ContractStatus status,
                                   Instant issuedAt, Long issuedBy, @Nullable Instant buyerAcknowledgedAt) {
        return new Contract(id, reservationId, transactionId, buyerId, lotId, documentId, objectKey, sizeBytes, status,
                issuedAt, issuedBy, buyerAcknowledgedAt);
    }

    public static Contract restore(Long id, Long reservationId, UUID transactionId, Long buyerId, Long lotId,
                                   UUID documentId, String objectKey, long sizeBytes, ContractStatus status,
                                   Instant issuedAt, Long issuedBy, @Nullable Instant buyerAcknowledgedAt,
                                   @Nullable CoOwner coOwner) {
        return restore(id, reservationId, transactionId, buyerId, lotId, documentId, objectKey, sizeBytes, status,
                issuedAt, issuedBy, buyerAcknowledgedAt, coOwner, ContractSignature.NONE);
    }

    public static Contract restore(Long id, Long reservationId, UUID transactionId, Long buyerId, Long lotId,
                                   UUID documentId, String objectKey, long sizeBytes, ContractStatus status,
                                   Instant issuedAt, Long issuedBy, @Nullable Instant buyerAcknowledgedAt,
                                   @Nullable CoOwner coOwner, ContractSignature signature) {
        var contract = restore(id, reservationId, transactionId, buyerId, lotId, documentId, objectKey, sizeBytes,
                status, issuedAt, issuedBy, buyerAcknowledgedAt);
        contract.coOwner = coOwner;
        contract.documentSha256 = signature.documentSha256();
        contract.signatureSubmissionId = signature.submissionId();
        contract.signingUrl = signature.signingUrl();
        contract.signatureRequestedAt = signature.requestedAt();
        contract.signedAt = signature.signedAt();
        contract.signedDocument = signature.signedDocument();
        contract.signatureDeclinedAt = signature.declinedAt();
        contract.signatureDeclineReason = signature.declineReason();
        return contract;
    }

    /** The signature as it is stored. */
    public ContractSignature getSignature() {
        return new ContractSignature(documentSha256, signatureSubmissionId, signingUrl, signatureRequestedAt, signedAt,
                signedDocument, signatureDeclinedAt, signatureDeclineReason);
    }

    public @Nullable CoOwner getCoOwner() { return coOwner; }

    public @Nullable Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public UUID getTransactionId() { return transactionId; }
    public Long getBuyerId() { return buyerId; }
    public Long getLotId() { return lotId; }
    public UUID getDocumentId() { return documentId; }
    public String getObjectKey() { return objectKey; }
    public long getSizeBytes() { return sizeBytes; }
    public ContractStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Long getIssuedBy() { return issuedBy; }
    public @Nullable Instant getBuyerAcknowledgedAt() { return buyerAcknowledgedAt; }
    public @Nullable String getDocumentSha256() { return documentSha256; }
    public @Nullable Long getSignatureSubmissionId() { return signatureSubmissionId; }
    public @Nullable String getSigningUrl() { return signingUrl; }
    public @Nullable Instant getSignatureRequestedAt() { return signatureRequestedAt; }
    public @Nullable Instant getSignedAt() { return signedAt; }
    public @Nullable SignedContractDocument getSignedDocument() { return signedDocument; }
    public @Nullable Instant getSignatureDeclinedAt() { return signatureDeclinedAt; }
    public @Nullable String getSignatureDeclineReason() { return signatureDeclineReason; }
}
