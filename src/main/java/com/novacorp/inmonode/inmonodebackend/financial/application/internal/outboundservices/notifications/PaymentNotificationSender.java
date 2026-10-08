package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications;

/**
 * Outbound port that tells buyers about their installments (US-24). Each method answers whether the notice was
 * delivered: one that was not is tried again in the next review.
 */
public interface PaymentNotificationSender {

    /** Scenario 1: the installment falls due within a few days. */
    boolean remindUpcoming(PaymentNotice notice);

    /** Scenario 2: the installment fell overdue and now carries its late fee. */
    boolean notifyOverdue(PaymentNotice notice);
}
