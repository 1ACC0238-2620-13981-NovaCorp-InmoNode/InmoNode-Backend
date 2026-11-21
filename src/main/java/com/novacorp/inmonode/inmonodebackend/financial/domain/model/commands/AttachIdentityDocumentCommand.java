package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.IdentityDocument;

/**
 * Attaches to one of the agent's field reservations the photo of the buyer's identity document it already uploaded
 * (US-59).
 */
public record AttachIdentityDocumentCommand(IdentityDocument document) {
}
