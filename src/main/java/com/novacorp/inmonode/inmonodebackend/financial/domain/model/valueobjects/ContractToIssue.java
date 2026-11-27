package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One entry of the contract queue: a verified web reservation without a contract yet, with the lot and project it
 * buys.
 *
 * @param buyerEmail the account of the buyer, who will read and sign the contract; {@code null} when it no longer
 *                   exists
 */
public record ContractToIssue(Reservation reservation, Lot lot, Project project, @Nullable String buyerEmail) {
    public ContractToIssue {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(lot, "lot");
        Objects.requireNonNull(project, "project");
    }
}
