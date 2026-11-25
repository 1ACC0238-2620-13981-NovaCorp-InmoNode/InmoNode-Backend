package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature;

/**
 * What the signature sheet of a contract cites (US-56). The buyer reads the contract itself in the app; the sheet
 * ties the signature to it through the hash of its PDF.
 *
 * @param signerEmail    the buyer's email, which the provider requires; it sends nothing to it
 * @param contract       how the sheet names the contract, e.g. {@code N.° 42}
 * @param buyer          the buyer, and the co-owner when there is one
 * @param lot            the project and the lot
 * @param issuedOn       the issue date, as the sheet shows it
 * @param documentSha256 the SHA-256 of the issued PDF
 */
public record SignatureSheet(long contractId, String signerEmail, String contract, String buyer, String lot,
                             String issuedOn, String documentSha256) {

    public String externalId() {
        return SignatureProvider.externalIdOf(contractId);
    }
}
