package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.acl.QuotationSnapshotFacade;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
public class ExternalQuotationSnapshotService {
    private final QuotationSnapshotFacade quotations;
    public ExternalQuotationSnapshotService(QuotationSnapshotFacade quotations) { this.quotations = quotations; }

    public List<Installment> scheduleFor(Reservation reservation, LocalDate issuedOn) {
        var plan = reservation.getFinancingPlan();
        if (plan == null || plan.quotationId() == null) throw new IllegalArgumentException("quotation reference is required");
        var quote = quotations.findById(plan.quotationId()).orElseThrow(() -> new IllegalStateException("accepted quotation not found"));
        if (quote.price().compareTo(plan.lotPrice().amount()) != 0
                || quote.initialPayment().compareTo(reservation.getInitialAmount().amount()) != 0
                || !quote.currency().equals(plan.lotPrice().currency()) || quote.termMonths() != plan.termMonths()
                || quote.annualInterestRate().compareTo(plan.annualInterestRate()) != 0) {
            throw new IllegalStateException("accepted quotation differs from the frozen reservation terms");
        }
        return quote.installments().stream().map(i -> Installment.scheduled(i.number(), issuedOn.plusMonths(i.number()),
                i.principal(), i.interest())).toList();
    }
}
