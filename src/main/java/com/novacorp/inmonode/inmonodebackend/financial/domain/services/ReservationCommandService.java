package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ConsolidateFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReleaseExpiredLotBlocksCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestWebReservationCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FieldReservationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.WebReservationOutcome;

/**
 * Command side of reservations: this context is the only authority on lot availability (Context Map).
 */
public interface ReservationCommandService {

    /**
     * US-11, US-12, US-32: consolidates one field reservation in its own transaction. The first reservation to reach
     * the server takes the lot; a later one is kept as a conflict; a re-send is answered as a duplicate.
     */
    FieldReservationOutcome handle(ConsolidateFieldReservationCommand command);

    /**
     * US-19: blocks a lot of a published project for a buyer's web separation request, for one hour. The first request
     * to lock the lot takes it; a later one is answered {@code LOT_UNAVAILABLE} and nothing is stored. Idempotent by
     * the request's transaction id.
     */
    WebReservationOutcome handle(RequestWebReservationCommand command);

    /**
     * Releases the expired lot blocks; run periodically. A block that ran out already counts as available, so this
     * only brings the stored status (catalog, portfolio) up to date.
     *
     * @return how many lots were released
     */
    int handle(ReleaseExpiredLotBlocksCommand command);

    /**
     * US-20, 2.6.4.1: attaches a voucher to its reservation as payment evidence. On time, the reservation and its lot
     * wait for verification ({@code PENDING_VERIFICATION}); late, the evidence is kept for the back office and nothing
     * else changes. Idempotent by the voucher id: a voucher already attached is answered with its evidence.
     *
     * @throws IllegalStateException when there is no reservation with that id, which the vouchers context only knows
     *                               after this context consolidated it
     */
    PaymentEvidence handle(ReceiveVoucherEvidenceCommand command);
}
