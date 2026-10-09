package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationContractQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationContract;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Query side of the contracts, for the buyers.
 */
public interface ContractQueryService {

    /**
     * US-21: the contract of a reservation of the caller with a link to download it, or that it is still in
     * preparation. Fails with {@code RESERVATION_NOT_FOUND} when the reservation does not exist or is not the caller's.
     */
    Result<ReservationContract, ApplicationError> handle(GetReservationContractQuery query);
}
