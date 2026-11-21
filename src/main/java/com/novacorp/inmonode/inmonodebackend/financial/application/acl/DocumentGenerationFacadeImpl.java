package com.novacorp.inmonode.inmonodebackend.financial.application.acl;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentRequestCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.DocumentGenerationFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.DocumentRequestReceipt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentGenerationFacadeImpl implements DocumentGenerationFacade {

    private final DocumentRequestCommandService documents;

    public DocumentGenerationFacadeImpl(DocumentRequestCommandService documents) {
        this.documents = documents;
    }

    @Override
    public DocumentRequestReceipt requestQuotationPdf(Long quotationId, Long buyerId, String title, List<String> lines) {
        var request = documents.request(DocumentKind.QUOTATION, String.valueOf(quotationId), buyerId,
                new DocumentContent(title, lines));
        return new DocumentRequestReceipt(request.getId(), request.getStatus().name(), request.getRequestedAt(),
                request.getFailureCode());
    }
}
