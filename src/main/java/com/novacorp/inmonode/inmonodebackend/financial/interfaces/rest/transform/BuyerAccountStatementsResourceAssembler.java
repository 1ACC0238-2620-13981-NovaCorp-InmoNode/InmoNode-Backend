package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.BuyerAccountStatements;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.BuyerAccountStatementsResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.BuyerAccountStatementsResource.NextInstallmentResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.BuyerAccountStatementsResource.StatementResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.BuyerAccountStatementsResource.TotalsResource;

public final class BuyerAccountStatementsResourceAssembler {

    private BuyerAccountStatementsResourceAssembler() {}

    public static BuyerAccountStatementsResource toResourceFromStatements(BuyerAccountStatements statements) {
        return new BuyerAccountStatementsResource(
                statements.totals().stream()
                        .map(totals -> new TotalsResource(totals.currency(), totals.lots(), totals.invested(),
                                totals.debt(), totals.progressPercentage()))
                        .toList(),
                statements.statements().stream().map(BuyerAccountStatementsResourceAssembler::toResource).toList());
    }

    private static StatementResource toResource(BuyerAccountStatements.Entry entry) {
        var statement = entry.statement();
        return new StatementResource(statement.getId(), statement.getTransactionId(), entry.projectId(),
                entry.projectName(), statement.getLotId(), entry.lotCode(), statement.getCurrency(),
                statement.getLotPrice(), statement.totalAmount(), statement.paidAmount(), statement.balance(),
                statement.progressPercentage(), statement.isFullyPaid(), statement.overdueInstallmentCount(),
                statement.nextInstallment().map(BuyerAccountStatementsResourceAssembler::toResource).orElse(null),
                entry.dueSoon(), statement.getOpenedAt());
    }

    private static NextInstallmentResource toResource(Installment installment) {
        return new NextInstallmentResource(installment.getNumber(), installment.getDueDate(),
                installment.amountDue(), installment.getStatus().name());
    }
}
