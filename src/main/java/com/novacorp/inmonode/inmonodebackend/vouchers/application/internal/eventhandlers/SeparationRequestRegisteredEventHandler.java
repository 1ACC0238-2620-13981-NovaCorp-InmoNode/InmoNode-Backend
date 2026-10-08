package com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.eventhandlers;

import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.events.SeparationRequestRegisteredEvent;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RecordWebReservationCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.ReservationOperationCommandService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer: turns the quoting context's "Solicitud de separación registrada" into this context's
 * reservation operation, the same concept a field reservation becomes, so a buyer's voucher is accepted like an
 * agent's (US-20). Synchronous, inside the transaction that blocks the lot: if it fails, the separation is not
 * registered either.
 */
@Component
public class SeparationRequestRegisteredEventHandler {

    private final ReservationOperationCommandService operationCommandService;

    public SeparationRequestRegisteredEventHandler(ReservationOperationCommandService operationCommandService) {
        this.operationCommandService = operationCommandService;
    }

    @EventListener
    public void on(SeparationRequestRegisteredEvent event) {
        operationCommandService.handle(new RecordWebReservationCommand(event.transactionId(), event.buyerId(),
                event.lotId(), event.initialAmount(), event.requestedAt(), event.lockExpiresAt()));
    }
}
