package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Consolidates a reservation registered offline by a field agent (US-11, US-12) against the central availability.
 *
 * @param sourceEventId id generated on the device for the reservation; a re-send carries the same one
 * @param prospectId    id generated on the device for the prospect, who lives in the field context
 * @param reservedAt    when the agent registered it on the device
 */
public record ConsolidateFieldReservationCommand(UUID sourceEventId, Long lotId, Long agentId, UUID prospectId,
                                                 Money initialAmount, Instant reservedAt) {
}
