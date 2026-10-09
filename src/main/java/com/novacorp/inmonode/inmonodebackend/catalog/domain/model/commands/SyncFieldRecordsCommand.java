package com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.RegisterProspectsCommand.ProspectData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the calling agent registered offline, sent in one go when the device is back online (US-11, US-32).
 * The agent is the authenticated caller, never a value of the payload.
 */
public record SyncFieldRecordsCommand(List<ProspectData> prospects, List<ReservationData> reservations) {

    /**
     * @param reservationId id generated on the device; a re-send carries the same one
     * @param prospectId    a prospect of this sync or one the agent synchronized before
     */
    public record ReservationData(UUID reservationId, Long lotId, UUID prospectId, BigDecimal initialAmount,
                                  Instant reservedAt) {
    }
}
