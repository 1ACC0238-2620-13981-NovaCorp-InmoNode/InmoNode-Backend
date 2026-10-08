package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.entities.ScheduledInstallment;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources.QuotationResource;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources.QuotationResource.InstallmentResource;

public final class QuotationResourceAssembler {

    private QuotationResourceAssembler() {}

    public static QuotationResource toResourceFromQuotation(Quotation quotation) {
        return new QuotationResource(quotation.getId(), quotation.getLotId(), quotation.getProjectId(),
                quotation.getLotCode(), quotation.getLotPrice().currency(), quotation.getLotPrice().amount(),
                quotation.getInitialPayment().amount(), quotation.initialPercentage(),
                quotation.financedAmount().amount(), quotation.getTermMonths(), quotation.getAnnualInterestRate(),
                quotation.monthlyInstallment().amount(), quotation.totalInterest().amount(),
                quotation.totalToPay().amount(), quotation.getGeneratedAt(), quotation.getValidUntil(),
                quotation.getInstallments().stream().map(QuotationResourceAssembler::toResource).toList());
    }

    private static InstallmentResource toResource(ScheduledInstallment installment) {
        return new InstallmentResource(installment.number(), installment.dueDate(), installment.amount().amount(),
                installment.principal().amount(), installment.interest().amount(), installment.balance().amount());
    }
}
