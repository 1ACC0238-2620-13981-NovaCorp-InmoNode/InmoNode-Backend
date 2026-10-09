package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.acl;

import java.math.BigDecimal;
import java.util.*;

/** Internal read of accepted terms, including expired quotations; deliberately independent of current catalog price. */
public interface QuotationSnapshotFacade {
    Optional<Snapshot> findById(Long quotationId);
    record Snapshot(BigDecimal price, BigDecimal initialPayment, String currency, int termMonths,
                    BigDecimal annualInterestRate, List<Quota> installments) {
        public Snapshot { installments = List.copyOf(installments); }
    }
    record Quota(int number, BigDecimal principal, BigDecimal interest) {}
}
