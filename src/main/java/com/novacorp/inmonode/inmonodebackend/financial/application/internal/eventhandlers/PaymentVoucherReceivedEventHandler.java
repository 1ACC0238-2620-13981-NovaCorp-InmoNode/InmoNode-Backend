package com.novacorp.inmonode.inmonodebackend.financial.application.internal.eventhandlers;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.events.PaymentVoucherReceivedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer ("PaymentEvidenceReceivedEventHandler" in 2.6.4): turns the vouchers context's "Comprobante
 * de pago recibido" into this context's payment evidence. Synchronous, inside the transaction that registers the
 * voucher: if it fails, the voucher is not registered either and the device sends it again.
 */
@Component
public class PaymentVoucherReceivedEventHandler {

    private final ReservationCommandService reservationCommandService;

    public PaymentVoucherReceivedEventHandler(ReservationCommandService reservationCommandService) {
        this.reservationCommandService = reservationCommandService;
    }

    @EventListener
    public void on(PaymentVoucherReceivedEvent event) {
        reservationCommandService.handle(new ReceiveVoucherEvidenceCommand(event.reservationId(), event.voucherId(),
                new Money(event.amount(), event.currency()), event.operationDate(), event.operationCode(),
                event.manuallyCorrected(), event.objectKey(), event.receivedAt()));
    }
}
