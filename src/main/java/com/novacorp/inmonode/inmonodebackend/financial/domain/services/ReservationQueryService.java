package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationPaymentsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationPayments;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Query side of the reservations, for the people who made them.
 */
public interface ReservationQueryService {

    /**
     * US-25: the evidences of a reservation of the caller, oldest first. Fails with {@code RESERVATION_NOT_FOUND}
     * when it does not exist or someone else made it, so another's reservation cannot be told apart from a missing one.
     */
    Result<ReservationPayments, ApplicationError> handle(GetReservationPaymentsQuery query);
}
