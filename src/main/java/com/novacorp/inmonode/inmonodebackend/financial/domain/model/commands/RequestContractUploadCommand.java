package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import java.util.UUID;

/**
 * The back office asks for a presigned URL to upload the contract PDF of a reservation.
 *
 * @param transactionId id of the reservation shared by every context
 * @param documentId    id the back office generates for the upload; send the same one to issue the contract
 * @param sizeBytes     exact size of the PDF, up to 10 MB
 */
public record RequestContractUploadCommand(UUID transactionId, UUID documentId, long sizeBytes) {
}
