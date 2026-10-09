package com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.valueobjects.FieldSyncResult;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.valueobjects.ReservationSyncOutcome;
import com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.resources.FieldSyncResultResource;
import com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.resources.FieldSyncResultResource.ReservationResultResource;

public final class FieldSyncResultResourceAssembler {

    private FieldSyncResultResourceAssembler() {}

    public static FieldSyncResultResource toResourceFromResult(FieldSyncResult result) {
        return new FieldSyncResultResource(result.prospectsSynced(),
                result.reservations().stream().map(FieldSyncResultResourceAssembler::toResource).toList());
    }

    private static ReservationResultResource toResource(ReservationSyncOutcome outcome) {
        return new ReservationResultResource(outcome.reservationId(), outcome.result().name(),
                outcome.reservationStatus(), outcome.blockedUntil(), outcome.conflictReason(),
                outcome.originalResult() == null ? null : outcome.originalResult().name());
    }
}
