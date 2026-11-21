package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestView;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.DocumentRequestResource;

import java.util.UUID;

public final class DocumentRequestResourceAssembler {

    private DocumentRequestResourceAssembler() {
    }

    /** Where a request is read again: {@code /api/v1/document-requests/{requestId}}. */
    public static String statusUrl(UUID requestId) {
        return "/api/v1/document-requests/" + requestId;
    }

    /** The answer to a new request, as it was saved: processing, or failed when the queue refused it. */
    public static DocumentRequestResource toResourceFromRequest(DocumentRequest request) {
        return new DocumentRequestResource(request.getId(), request.getKind().name(), request.getReference(),
                request.getStatus().name(), request.getRequestedAt(), request.getCompletedAt(), request.getSizeBytes(),
                null, null, request.getFailureCode(), statusUrl(request.getId()));
    }

    public static DocumentRequestResource toResourceFromView(DocumentRequestView view) {
        var request = view.request();
        var download = view.download();
        return new DocumentRequestResource(request.getId(), request.getKind().name(), request.getReference(),
                view.status().name(), request.getRequestedAt(), request.getCompletedAt(), request.getSizeBytes(),
                download == null ? null : download.url().toString(), download == null ? null : download.expiresAt(),
                view.failureCode(), statusUrl(request.getId()));
    }
}
