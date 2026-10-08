package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands;

/**
 * The calling buyer asks to separate a lot from the web portal (US-19), backed by a valid quotation of that lot.
 */
public record RequestLotSeparationCommand(Long lotId, Long quotationId) {
}
