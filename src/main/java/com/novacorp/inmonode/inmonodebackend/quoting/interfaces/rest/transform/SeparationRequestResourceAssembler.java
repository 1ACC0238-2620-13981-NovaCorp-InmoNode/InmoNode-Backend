package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.SeparationRequest;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources.SeparationRequestResource;

public final class SeparationRequestResourceAssembler {

    private SeparationRequestResourceAssembler() {}

    public static SeparationRequestResource toResourceFromRequest(SeparationRequest request) {
        return new SeparationRequestResource(request.getId(), request.getTransactionId(), request.getLotId(),
                request.getQuotationId(), request.getStatus().name(), request.getInitialAmount().amount(),
                request.getInitialAmount().currency(), request.getRequestedAt(), request.getLockExpiresAt());
    }
}
