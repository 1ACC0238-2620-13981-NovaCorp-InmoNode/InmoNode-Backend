package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.quoting.application.internal.documents.QuotationDocumentContent;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.queries.*;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.*;
import com.novacorp.inmonode.inmonodebackend.shared.application.documents.PdfGenerator;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.*;
import org.springframework.stereotype.Service;

@Service
public class QuotationDocumentQueryServiceImpl implements QuotationDocumentQueryService {
    private final QuotationQueryService quotations;
    private final PdfGenerator pdf;

    public QuotationDocumentQueryServiceImpl(QuotationQueryService quotations, PdfGenerator pdf) {
        this.quotations = quotations;
        this.pdf = pdf;
    }

    @Override
    public Result<byte[], ApplicationError> handle(DownloadQuotationQuery query) {
        return quotations.handle(new GetQuotationQuery(query.quotationId())).map(quote ->
                pdf.generateReadOnly(QuotationDocumentContent.TITLE, QuotationDocumentContent.lines(quote)));
    }
}
