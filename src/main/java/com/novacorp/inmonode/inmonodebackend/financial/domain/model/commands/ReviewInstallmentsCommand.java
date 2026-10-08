package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import java.time.LocalDate;

/**
 * The daily review of the installments (US-24): late fees on the overdue ones and notices to their buyers.
 *
 * @param asOfDate the day in Lima being reviewed; an installment due before it is overdue
 */
public record ReviewInstallmentsCommand(LocalDate asOfDate) {

    public ReviewInstallmentsCommand {
        if (asOfDate == null) {
            throw new IllegalArgumentException("asOfDate is required");
        }
    }
}
