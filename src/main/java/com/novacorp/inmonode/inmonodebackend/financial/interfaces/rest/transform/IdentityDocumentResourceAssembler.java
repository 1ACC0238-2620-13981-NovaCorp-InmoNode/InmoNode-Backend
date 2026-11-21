package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocumentRegistration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocumentUpload;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.IdentityDocumentFileResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.IdentityDocumentRegistrationResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.IdentityDocumentUploadResource;

import java.util.UUID;

public final class IdentityDocumentResourceAssembler {

    private IdentityDocumentResourceAssembler() {}

    /** @throws IllegalArgumentException for another type or a size out of range, answered as 400 */
    public static IdentityDocument toDocument(UUID transactionId, IdentityDocumentFileResource resource) {
        return new IdentityDocument(transactionId, resource.documentId(), resource.contentType(), resource.sizeBytes());
    }

    public static IdentityDocumentUploadResource toResourceFromUpload(IdentityDocumentUpload upload) {
        return new IdentityDocumentUploadResource(upload.uploadUrl().toString(), upload.objectKey(),
                upload.expiresAt(), upload.headers());
    }

    public static IdentityDocumentRegistrationResource toResourceFromRegistration(
            IdentityDocumentRegistration registration) {
        var document = registration.document();
        return new IdentityDocumentRegistrationResource(document.transactionId(), document.documentId(),
                document.contentType(), document.sizeBytes(), registration.attachedAt(),
                registration.result().name());
    }
}
