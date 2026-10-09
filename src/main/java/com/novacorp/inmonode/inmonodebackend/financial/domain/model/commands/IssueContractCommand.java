package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import java.util.UUID;

/**
 * The back office issues the contract of a reservation with the PDF it already uploaded (2.6.4: IssueContractCommand).
 *
 * @param documentId the id used to ask for the upload URL
 * @param sizeBytes  the size declared for the upload
 */
public record IssueContractCommand(UUID transactionId, UUID documentId, long sizeBytes) {
}
