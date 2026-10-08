package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ConsolidateFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReleaseExpiredLotBlocksCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FieldReservationOutcome;

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
     * Releases the expired lot blocks; run periodically. A block that ran out already counts as available, so this
     * only brings the stored status (catalog, portfolio) up to date.
     *
     * @return how many lots were released
     */
    int handle(ReleaseExpiredLotBlocksCommand command);
}
