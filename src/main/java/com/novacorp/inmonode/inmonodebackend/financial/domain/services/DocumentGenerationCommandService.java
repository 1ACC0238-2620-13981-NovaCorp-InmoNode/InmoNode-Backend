package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.GenerateDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentGenerationOutcome;

/**
 * The document consumer (US-45): renders a requested PDF, stores it in the file repository and records the outcome.
 * It runs behind the queue, never in the request that asked for the document.
 */
public interface DocumentGenerationCommandService {

    /**
     * Renders and stores the PDF of a pending request. A request that is unknown or already finished is skipped, so
     * a redelivered message does nothing; one past its deadline is marked failed. Rendering that breaks or takes
     * longer than its limit fails the request and notifies the finance back office.
     */
    DocumentGenerationOutcome handle(GenerateDocumentCommand command);
}
