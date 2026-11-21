package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands;

import java.util.Objects;

/** A buyer asks for the PDF of their quotation to be generated in the background (US-45). */
public record RequestQuotationDocumentCommand(Long quotationId) {

    public RequestQuotationDocumentCommand {
        Objects.requireNonNull(quotationId, "quotationId");
    }
}
