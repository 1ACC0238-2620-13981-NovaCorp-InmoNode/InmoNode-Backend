package com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.entities.ScheduledInstallment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.entities.QuotationEntity;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.entities.QuotationInstallmentEntity;

import java.math.BigDecimal;

/**
 * Translates between the {@link Quotation} aggregate and its JPA persistence entity.
 */
public final class QuotationEntityAssembler {

    private QuotationEntityAssembler() {}

    public static Quotation toDomain(QuotationEntity entity) {
        var currency = entity.getCurrency();
        var installments = entity.getInstallments().stream()
                .map(installment -> toDomain(installment, currency))
                .toList();
        return Quotation.restore(entity.getId(), entity.getBuyerId(), entity.getLotId(), entity.getProjectId(),
                entity.getLotCode(), new Money(entity.getLotPrice(), currency),
                new Money(entity.getInitialPayment(), currency), entity.getTermMonths(),
                entity.getAnnualInterestRate(), installments, entity.getGeneratedAt(), entity.getValidUntil());
    }

    /**
     * Copies the aggregate state onto a new entity. A quotation never changes once simulated, so only new ones are
     * saved.
     */
    public static QuotationEntity toEntity(Quotation quotation) {
        var entity = new QuotationEntity();
        entity.setBuyerId(quotation.getBuyerId());
        entity.setLotId(quotation.getLotId());
        entity.setProjectId(quotation.getProjectId());
        entity.setLotCode(quotation.getLotCode());
        entity.setLotPrice(quotation.getLotPrice().amount());
        entity.setCurrency(quotation.getLotPrice().currency());
        entity.setInitialPayment(quotation.getInitialPayment().amount());
        entity.setTermMonths(quotation.getTermMonths());
        entity.setAnnualInterestRate(quotation.getAnnualInterestRate());
        entity.setGeneratedAt(quotation.getGeneratedAt());
        entity.setValidUntil(quotation.getValidUntil());
        for (var installment : quotation.getInstallments()) {
            entity.getInstallments().add(toEntity(installment, entity));
        }
        return entity;
    }

    private static ScheduledInstallment toDomain(QuotationInstallmentEntity entity, String currency) {
        return new ScheduledInstallment(entity.getNumber(), entity.getDueDate(), money(entity.getAmount(), currency),
                money(entity.getPrincipal(), currency), money(entity.getInterest(), currency),
                money(entity.getBalance(), currency));
    }

    private static QuotationInstallmentEntity toEntity(ScheduledInstallment installment, QuotationEntity quotation) {
        var entity = new QuotationInstallmentEntity();
        entity.setQuotation(quotation);
        entity.setNumber(installment.number());
        entity.setDueDate(installment.dueDate());
        entity.setAmount(installment.amount().amount());
        entity.setPrincipal(installment.principal().amount());
        entity.setInterest(installment.interest().amount());
        entity.setBalance(installment.balance().amount());
        return entity;
    }

    private static Money money(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
}
