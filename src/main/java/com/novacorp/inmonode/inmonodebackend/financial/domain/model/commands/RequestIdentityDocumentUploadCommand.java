package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocument;

/**
 * Asks for a presigned URL to upload the photo of the buyer's identity document of one of the agent's field
 * reservations (US-59).
 */
public record RequestIdentityDocumentUploadCommand(IdentityDocument document) {
}
