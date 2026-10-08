package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterInstallmentPaymentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReviewInstallmentsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.AccountStatementView;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentReviewSummary;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Command side of the account statements: the back office and the daily review.
 */
public interface AccountStatementCommandService {

    /**
     * US-23: records the payment of an installment; with the last one the lot is {@code SOLD}. Fails with
     * {@code ACCOUNT_STATEMENT_NOT_FOUND} or {@code INSTALLMENT_NOT_FOUND}, {@code INSTALLMENT_CONFLICT} when it is
     * already paid and {@code BUSINESS_RULE_VIOLATION} when the amount is not exactly what is due.
     */
    Result<AccountStatementView, ApplicationError> handle(RegisterInstallmentPaymentCommand command);

    /**
     * US-24: unpaid installments past due fall overdue with the late fee of their project; their buyers are told,
     * and buyers of installments due within 5 days are reminded. Each notice is sent once; one that could not be
     * delivered is tried again in the next review. Every statement is reviewed in its own transaction.
     */
    InstallmentReviewSummary handle(ReviewInstallmentsCommand command);
}
