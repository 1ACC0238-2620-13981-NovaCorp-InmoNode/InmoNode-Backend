package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureInvitation;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProvider;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProviderException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSheet;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSubmission;

/** Stands in for DocuSeal while it is not configured: the requests to sign wait for it. */
public class DisabledSignatureProvider implements SignatureProvider {

    private static final String NOT_CONFIGURED = "the signature provider is not configured";

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public SignatureInvitation requestSignature(SignatureSheet sheet) {
        throw new SignatureProviderException(NOT_CONFIGURED);
    }

    @Override
    public SignatureSubmission fetchSubmission(long submissionId) {
        throw new SignatureProviderException(NOT_CONFIGURED);
    }

    @Override
    public byte[] downloadSignedDocument(SignatureSubmission submission) {
        throw new SignatureProviderException(NOT_CONFIGURED);
    }
}
