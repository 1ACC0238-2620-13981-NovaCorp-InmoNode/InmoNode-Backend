package com.novacorp.inmonode.inmonodebackend.catalog.domain.services;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.RegisterProspectsCommand;

/**
 * Command side of the field prospects.
 */
public interface ProspectCommandService {

    /**
     * US-04, US-11: stores new prospects and updates the contact info of those already sent, in one transaction.
     * Re-sending a prospect is therefore idempotent. A prospect registered by another agent is left untouched.
     *
     * @return how many prospects were stored or updated
     * @throws IllegalArgumentException when a prospect has invalid data; nothing is stored then
     */
    int handle(RegisterProspectsCommand command);
}
