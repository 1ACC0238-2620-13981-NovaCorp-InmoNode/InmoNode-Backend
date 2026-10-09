package com.novacorp.inmonode.inmonodebackend.quoting.application.acl;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.repositories.QuotationRepository;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.acl.QuotationSnapshotFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class QuotationSnapshotFacadeImpl implements QuotationSnapshotFacade {
    private final QuotationRepository quotations;
    public QuotationSnapshotFacadeImpl(QuotationRepository quotations) { this.quotations = quotations; }

    @Override
    @Transactional(readOnly = true)
    public Optional<Snapshot> findById(Long quotationId) {
        return quotations.findById(quotationId).map(q -> new Snapshot(q.getLotPrice().amount(),
                q.getInitialPayment().amount(), q.getLotPrice().currency(), q.getTermMonths(), q.getAnnualInterestRate(),
                q.getInstallments().stream().map(i -> new Quota(i.number(), i.principal().amount(), i.interest().amount())).toList()));
    }
}
