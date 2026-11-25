package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature;

/**
 * A submission the provider opened for a contract.
 *
 * @param signingUrl where the buyer signs it; whoever has it can sign, so it is only shown to the buyer
 */
public record SignatureInvitation(long submissionId, String signingUrl) {
}
