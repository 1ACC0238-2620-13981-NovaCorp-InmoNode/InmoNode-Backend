package com.novacorp.inmonode.inmonodebackend.quoting.domain.services;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands.RequestQuotationDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.QuotationDocumentRequest;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/** Background PDFs of the quotations (US-45). */
public interface QuotationDocumentCommandService {

    /**
     * Queues the PDF of the caller's quotation, with the same content as the download, and answers at once.
     * {@code QUOTATION_NOT_FOUND} for an unknown quotation or another buyer's.
     */
    Result<QuotationDocumentRequest, ApplicationError> handle(RequestQuotationDocumentCommand command);
}
