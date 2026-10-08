package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.AccountStatementView;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.AccountStatementResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.AccountStatementResource.InstallmentResource;

public final class AccountStatementResourceAssembler {

    private AccountStatementResourceAssembler() {}

    public static AccountStatementResource toResourceFromView(AccountStatementView view) {
        var statement = view.statement();
        return new AccountStatementResource(statement.getId(), statement.getTransactionId(), statement.getLotId(),
                statement.getCurrency(), statement.getLotPrice(), statement.getInitialPayment(),
                statement.financedAmount(), statement.getTermMonths(), statement.getAnnualInterestRate(),
                statement.totalAmount(), statement.paidAmount(), statement.balance(),
                statement.progressPercentage(), statement.isFullyPaid(), statement.getOpenedAt(),
                statement.nextInstallment().map(AccountStatementResourceAssembler::toResource).orElse(null),
                view.dueSoon(),
                statement.getInstallments().stream().map(AccountStatementResourceAssembler::toResource).toList());
    }

    private static InstallmentResource toResource(Installment installment) {
        return new InstallmentResource(installment.getNumber(), installment.getDueDate(), installment.getAmount(),
                installment.getPrincipal(), installment.getInterest(), installment.getPenalty(),
                installment.amountDue(), installment.getStatus().name(), installment.getPaidAt(),
                installment.getPaidAmount());
    }
}
