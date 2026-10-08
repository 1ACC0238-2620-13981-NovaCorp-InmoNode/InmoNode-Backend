package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPendingVerificationsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PendingVerification;

import java.util.List;

/**
 * Query side of the financial verification.
 */
public interface VerificationQueryService {

    /** The pending evidences, oldest first, late ones included so they can be rejected. */
    List<PendingVerification> handle(GetPendingVerificationsQuery query);
}
