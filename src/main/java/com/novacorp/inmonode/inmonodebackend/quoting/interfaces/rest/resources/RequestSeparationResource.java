package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

/**
 * @param quotationId a quotation of the same lot, still valid, that the buyer accepts
 */
public record RequestSeparationResource(@NotNull Long quotationId) {
}
