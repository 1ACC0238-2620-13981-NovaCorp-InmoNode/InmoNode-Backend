package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetDocumentRequestQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestView;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.DocumentRequestRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentRequestQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

@Service
public class DocumentRequestQueryServiceImpl implements DocumentRequestQueryService {

    /** Like the other downloads: the link to the PDF is valid for 10 minutes. */
    static final Duration DOWNLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final DocumentRequestRepository repository;
    private final ObjectStorage objectStorage;
    private final ExternalIamService externalIamService;
    private final Clock clock;

    public DocumentRequestQueryServiceImpl(DocumentRequestRepository repository, ObjectStorage objectStorage,
                                           ExternalIamService externalIamService, Clock clock) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.externalIamService = externalIamService;
        this.clock = clock;
    }

    @Override
    public Result<DocumentRequestView, ApplicationError> handle(GetDocumentRequestQuery query) {
        var readerId = externalIamService.currentUserId().orElse(null);
        var request = repository.findById(query.requestId())
                .filter(found -> readerId != null && canRead(found, readerId))
                .orElse(null);
        if (request == null) {
            return Result.failure(ApplicationError.notFound("document_request", query.requestId().toString()));
        }
        var now = clock.instant();
        var status = request.statusAt(now);
        var download = status == DocumentRequestStatus.READY
                ? objectStorage.presignDownload(request.getObjectKey(), DOWNLOAD_URL_VALIDITY)
                : null;
        return Result.success(new DocumentRequestView(request, status, request.failureCodeAt(now), download));
    }

    /** Whoever asked for it; the finance back office also reads every contract. Others do not learn it exists. */
    private boolean canRead(DocumentRequest request, Long readerId) {
        return request.getRequestedBy().equals(readerId)
                || (request.getKind() == DocumentKind.CONTRACT && externalIamService.isFinanceAdmin());
    }
}
