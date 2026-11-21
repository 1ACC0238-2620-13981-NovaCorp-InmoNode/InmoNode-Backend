package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;

import java.util.Objects;
import java.util.UUID;

/**
 * The order the document consumer takes from the queue (US-45): render {@code content} for request
 * {@code requestId} and store it. It is the JSON payload published to the message broker.
 */
public record GenerateDocumentCommand(UUID requestId, DocumentContent content) {

    public GenerateDocumentCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(content, "content");
    }
}
