package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.AccountStatementEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.InstallmentEntity;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Translates between the {@link AccountStatement} aggregate and its JPA persistence entity.
 */
public final class AccountStatementEntityAssembler {

    private AccountStatementEntityAssembler() {}

    public static AccountStatement toDomain(AccountStatementEntity entity) {
        return AccountStatement.restore(entity.getId(), entity.getContractId(), entity.getReservationId(),
                entity.getTransactionId(), entity.getBuyerId(), entity.getLotId(), entity.getCurrency(),
                entity.getLotPrice(), entity.getInitialPayment(), entity.getTermMonths(),
                entity.getAnnualInterestRate(), entity.getOpenedAt(),
                entity.getInstallments().stream().map(AccountStatementEntityAssembler::toDomain).toList());
    }

    /**
     * Copies the aggregate state onto the entity; audit columns and id stay untouched. Installments are matched by
     * number: new ones are added and known ones updated (payments, fees, notices).
     */
    public static AccountStatementEntity copyToEntity(AccountStatement statement, AccountStatementEntity entity) {
        entity.setContractId(statement.getContractId());
        entity.setReservationId(statement.getReservationId());
        entity.setTransactionId(statement.getTransactionId());
        entity.setBuyerId(statement.getBuyerId());
        entity.setLotId(statement.getLotId());
        entity.setCurrency(statement.getCurrency());
        entity.setLotPrice(statement.getLotPrice());
        entity.setInitialPayment(statement.getInitialPayment());
        entity.setTermMonths(statement.getTermMonths());
        entity.setAnnualInterestRate(statement.getAnnualInterestRate());
        entity.setOpenedAt(statement.getOpenedAt());
        var stored = entity.getInstallments().stream()
                .collect(Collectors.toMap(InstallmentEntity::getNumber, Function.identity()));
        for (var installment : statement.getInstallments()) {
            var installmentEntity = stored.get(installment.getNumber());
            if (installmentEntity == null) {
                installmentEntity = new InstallmentEntity();
                installmentEntity.setAccountStatement(entity);
                entity.getInstallments().add(installmentEntity);
            }
            copyToEntity(installment, installmentEntity);
        }
        return entity;
    }

    private static Installment toDomain(InstallmentEntity entity) {
        return Installment.restore(entity.getId(), entity.getNumber(), entity.getDueDate(), entity.getAmount(),
                entity.getPrincipal(), entity.getInterest(), entity.getStatus(), entity.getPaidAt(),
                entity.getPaidAmount(), entity.getPenalty(), entity.getReminderSentAt(),
                entity.getOverdueNotifiedAt());
    }

    private static void copyToEntity(Installment installment, InstallmentEntity entity) {
        entity.setNumber(installment.getNumber());
        entity.setDueDate(installment.getDueDate());
        entity.setAmount(installment.getAmount());
        entity.setPrincipal(installment.getPrincipal());
        entity.setInterest(installment.getInterest());
        entity.setStatus(installment.getStatus());
        entity.setPaidAt(installment.getPaidAt());
        entity.setPaidAmount(installment.getPaidAmount());
        entity.setPenalty(installment.getPenalty());
        entity.setReminderSentAt(installment.getReminderSentAt());
        entity.setOverdueNotifiedAt(installment.getOverdueNotifiedAt());
    }
}
