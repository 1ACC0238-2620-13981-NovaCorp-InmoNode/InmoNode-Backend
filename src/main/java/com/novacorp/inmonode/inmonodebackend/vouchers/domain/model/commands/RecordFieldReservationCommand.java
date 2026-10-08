package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a reservation made in the field, so its payment voucher can be accepted later.
 */
public record RecordFieldReservationCommand(UUID reservationId, Long agentId, Long lotId, BigDecimal initialAmount,
                                            Instant reservedAt, @Nullable Instant evidenceDueAt) {
}
