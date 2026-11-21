package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import java.util.Objects;
import java.util.UUID;

/**
 * The back office asks the backend to generate the contract PDF of a verified web reservation (US-45), instead of
 * uploading one made by hand.
 */
public record RequestContractDocumentCommand(UUID transactionId) {

    public RequestContractDocumentCommand {
        Objects.requireNonNull(transactionId, "transactionId");
    }
}
