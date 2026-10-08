package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;

/**
 * A lot of a published project, together with the project that sets its financing rules.
 */
public record PublishedLot(Project project, Lot lot) {
}
