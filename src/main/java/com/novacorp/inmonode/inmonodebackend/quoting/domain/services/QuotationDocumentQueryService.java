package com.novacorp.inmonode.inmonodebackend.quoting.domain.services;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.queries.DownloadQuotationQuery;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.*;

public interface QuotationDocumentQueryService {
    Result<byte[], ApplicationError> handle(DownloadQuotationQuery query);
}
