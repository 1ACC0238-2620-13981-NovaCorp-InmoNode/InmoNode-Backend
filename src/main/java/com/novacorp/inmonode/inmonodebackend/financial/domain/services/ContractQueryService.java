package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetContractsToIssueQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationContractQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractToIssue;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationContract;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;

/**
 * Query side of the contracts, for the buyers and the finance back office.
 */
public interface ContractQueryService {

    /**
     * US-21: one page of the reservations whose contract the back office can issue now, each with its lot, project
     * and buyer.
     */
    PageResult<ContractToIssue> handle(GetContractsToIssueQuery query);

    /**
     * US-21: the contract of a reservation of the caller with a link to download it, or that it is still in
     * preparation. Fails with {@code RESERVATION_NOT_FOUND} when the reservation does not exist or is not the caller's.
     */
    Result<ReservationContract, ApplicationError> handle(GetReservationContractQuery query);
}
