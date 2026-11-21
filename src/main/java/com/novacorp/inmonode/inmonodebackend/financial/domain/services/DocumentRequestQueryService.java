package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetDocumentRequestQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestView;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/** Query side of the background PDFs (US-45). */
public interface DocumentRequestQueryService {

    /**
     * The request, with a link to its PDF valid for 10 minutes once it is ready. Only whoever asked for it reads it,
     * and the finance back office reads every contract request; anyone else gets {@code DOCUMENT_REQUEST_NOT_FOUND}.
     */
    Result<DocumentRequestView, ApplicationError> handle(GetDocumentRequestQuery query);
}
