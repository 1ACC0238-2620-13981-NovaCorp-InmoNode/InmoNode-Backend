package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

/**
 * Makes available again every lot whose block ran out without payment evidence, and expires the reservations
 * that held them (Lot Block).
 */
public record ReleaseExpiredLotBlocksCommand() {
}
