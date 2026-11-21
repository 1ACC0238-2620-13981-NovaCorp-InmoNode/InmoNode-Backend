package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage.PresignedDownload;
import org.jspecify.annotations.Nullable;

/**
 * A background PDF as its reader sees it now (US-45).
 *
 * @param status      an expired request reads as {@link DocumentRequestStatus#FAILED}
 * @param failureCode why it failed; {@code null} unless it failed
 * @param download    a short-lived link to the PDF; only when it is ready
 */
public record DocumentRequestView(DocumentRequest request, DocumentRequestStatus status, @Nullable String failureCode,
                                  @Nullable PresignedDownload download) {
}
