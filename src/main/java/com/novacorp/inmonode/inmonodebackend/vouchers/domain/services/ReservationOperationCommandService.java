package com.novacorp.inmonode.inmonodebackend.vouchers.domain.services;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RecordFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RecordWebReservationCommand;

/**
 * Command side of the reservation operations.
 */
public interface ReservationOperationCommandService {

    /**
     * Idempotent: an operation already recorded is left as it is, since the announcement may arrive more than once.
     *
     * @return {@code true} when the operation was new
     */
    boolean handle(RecordFieldReservationCommand command);

    /**
     * Idempotent too: an operation already recorded is left as it is.
     *
     * @return {@code true} when the operation was new
     */
    boolean handle(RecordWebReservationCommand command);
}
