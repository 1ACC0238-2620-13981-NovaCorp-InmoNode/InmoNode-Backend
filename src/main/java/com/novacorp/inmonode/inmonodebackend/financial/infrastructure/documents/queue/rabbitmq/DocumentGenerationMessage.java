package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.queue.rabbitmq;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.GenerateDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;

import java.util.List;
import java.util.UUID;

/**
 * The JSON payload of a PDF request on the message broker (US-45):
 * {@code {"requestId": "...", "title": "...", "lines": ["...", ...]}}.
 */
public record DocumentGenerationMessage(UUID requestId, String title, List<String> lines) {

    static DocumentGenerationMessage from(GenerateDocumentCommand command) {
        return new DocumentGenerationMessage(command.requestId(), command.content().title(), command.content().lines());
    }

    GenerateDocumentCommand toCommand() {
        return new GenerateDocumentCommand(requestId, new DocumentContent(title, lines));
    }
}
