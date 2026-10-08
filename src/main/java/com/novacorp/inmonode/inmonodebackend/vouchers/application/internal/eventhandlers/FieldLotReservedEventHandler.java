package com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.eventhandlers;

import com.novacorp.inmonode.inmonodebackend.catalog.interfaces.events.FieldLotReservedEvent;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RecordFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.ReservationOperationCommandService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer: turns the field context's "Lote separado" into this context's reservation operation.
 * Synchronous: if it fails, the synchronization fails and the device retries; the retry announces the reservation
 * again and this handler, being idempotent, records it then.
 */
@Component
public class FieldLotReservedEventHandler {

    private final ReservationOperationCommandService operationCommandService;

    public FieldLotReservedEventHandler(ReservationOperationCommandService operationCommandService) {
        this.operationCommandService = operationCommandService;
    }

    @EventListener
    public void on(FieldLotReservedEvent event) {
        operationCommandService.handle(new RecordFieldReservationCommand(event.reservationId(), event.agentId(),
                event.lotId(), event.initialAmount(), event.reservedAt(), event.blockedUntil()));
    }
}
