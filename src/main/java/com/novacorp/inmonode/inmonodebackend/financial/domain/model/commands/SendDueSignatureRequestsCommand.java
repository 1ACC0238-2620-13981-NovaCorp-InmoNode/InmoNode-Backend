package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

/**
 * Sends to the signature provider the requests whose attempt is due (US-56).
 *
 * @param limit how many to send in this run, at most
 */
public record SendDueSignatureRequestsCommand(int limit) {

    public SendDueSignatureRequestsCommand {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
