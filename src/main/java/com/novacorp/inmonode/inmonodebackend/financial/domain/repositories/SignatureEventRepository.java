package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureEventRecord;

import java.util.Optional;

/**
 * The log of the signature provider's callbacks (US-56, Scenario 2).
 */
public interface SignatureEventRepository {

    Optional<SignatureEventRecord> findByKey(String eventKey);

    /**
     * Records a callback once. A REJECTED one is not final, since the provider may not have caught up yet when it
     * was received: recording the callback again replaces it.
     *
     * @return false when it was already recorded with another outcome, which is kept
     */
    boolean record(SignatureEventRecord event);
}
