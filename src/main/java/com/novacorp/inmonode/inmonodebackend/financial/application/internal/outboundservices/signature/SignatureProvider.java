package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Outbound port to the electronic signature provider (US-56): it receives the signature sheet of a contract, lets
 * the buyer sign it, and keeps the signed document. Every call may fail with {@link SignatureProviderException}.
 */
public interface SignatureProvider {

    /** Prefix of the reference that ties a submission to its contract: {@code inmonode-contract-{contractId}}. */
    String EXTERNAL_ID_PREFIX = "inmonode-contract-";

    Pattern EXTERNAL_ID = Pattern.compile(Pattern.quote(EXTERNAL_ID_PREFIX) + "(\\d{1,18})");

    /** Whether it is configured; without it the requests wait without spending attempts. */
    boolean isEnabled();

    /**
     * Sends the signature sheet. When the provider already has a live submission of the contract, such as one
     * created by an attempt that timed out, that one is returned instead of a second.
     */
    SignatureInvitation requestSignature(SignatureSheet sheet);

    /** The submission as the provider has it now. */
    SignatureSubmission fetchSubmission(long submissionId);

    /** The signed document of a completed submission. */
    byte[] downloadSignedDocument(SignatureSubmission submission);

    static String externalIdOf(long contractId) {
        return EXTERNAL_ID_PREFIX + contractId;
    }

    /** The contract a reference points to; empty for anything that is not a contract of InmoNode. */
    static Optional<Long> contractIdOf(String externalId) {
        if (externalId == null) {
            return Optional.empty();
        }
        var matcher = EXTERNAL_ID.matcher(externalId);
        return matcher.matches() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
    }
}
