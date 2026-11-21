package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.quoting.application.internal.documents.QuotationDocumentContent;
import com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl.ExternalDocumentGenerationService;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands.RequestQuotationDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.queries.GetQuotationQuery;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.QuotationDocumentRequest;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.QuotationDocumentCommandService;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.QuotationQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotationDocumentCommandServiceImpl implements QuotationDocumentCommandService {

    private final QuotationQueryService quotations;
    private final ExternalDocumentGenerationService documents;

    public QuotationDocumentCommandServiceImpl(QuotationQueryService quotations,
                                               ExternalDocumentGenerationService documents) {
        this.quotations = quotations;
        this.documents = documents;
    }

    /** In one transaction, so the request reaches the queue only once it is committed. */
    @Override
    @Transactional
    public Result<QuotationDocumentRequest, ApplicationError> handle(RequestQuotationDocumentCommand command) {
        return quotations.handle(new GetQuotationQuery(command.quotationId())).map(quote ->
                documents.requestPdf(quote.getId(), quote.getBuyerId(), QuotationDocumentContent.TITLE,
                        QuotationDocumentContent.lines(quote)));
    }
}
