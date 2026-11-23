package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The electronic signature of a contract as it is stored (US-56): the hash of the issued PDF the signature sheet
 * cites, the provider's submission, and how it ended.
 *
 * @param documentSha256 SHA-256 of the issued PDF; {@code null} for contracts issued before US-56
 * @param submissionId   the provider's submission the buyer signs
 * @param signingUrl     where the buyer signs it
 */
public record ContractSignature(@Nullable String documentSha256, @Nullable Long submissionId,
                                @Nullable String signingUrl, @Nullable Instant requestedAt,
                                @Nullable Instant signedAt, @Nullable SignedContractDocument signedDocument,
                                @Nullable Instant declinedAt, @Nullable String declineReason) {

    public static final ContractSignature NONE = new ContractSignature(null, null, null, null, null, null, null, null);
}
