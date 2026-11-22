package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPendingVerificationsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PendingVerification;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;

/**
 * Query side of the financial verification.
 */
public interface VerificationQueryService {

    /** One page of the pending evidences, oldest first, late ones included so they can be rejected. */
    PageResult<PendingVerification> handle(GetPendingVerificationsQuery query);
}
