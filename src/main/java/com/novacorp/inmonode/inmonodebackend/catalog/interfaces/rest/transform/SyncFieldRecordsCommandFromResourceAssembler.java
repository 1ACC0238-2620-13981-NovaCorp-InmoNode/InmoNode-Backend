package com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.RegisterProspectsCommand.ProspectData;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.SyncFieldRecordsCommand;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.SyncFieldRecordsCommand.ReservationData;
import com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.resources.FieldSyncResource;

public final class SyncFieldRecordsCommandFromResourceAssembler {

    private SyncFieldRecordsCommandFromResourceAssembler() {}

    public static SyncFieldRecordsCommand toCommandFromResource(FieldSyncResource resource) {
        var prospects = resource.prospects().stream()
                .map(prospect -> new ProspectData(prospect.id(), prospect.document(), prospect.fullName(),
                        prospect.phone(), prospect.registeredAt()))
                .toList();
        var reservations = resource.reservations().stream()
                .map(reservation -> new ReservationData(reservation.id(), reservation.lotId(), reservation.prospectId(),
                        reservation.initialAmount(), reservation.reservedAt()))
                .toList();
        return new SyncFieldRecordsCommand(prospects, reservations);
    }
}
