package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Status of an installment of the account statement (2.6.4.1).
 */
public enum InstallmentStatus {
    /** Not paid yet and not due yet. */
    PENDING,
    /** Paid, as the back office registered it. */
    PAID,
    /** Its due date passed without payment: it carries a late fee (US-24, Scenario 2). */
    OVERDUE
}
