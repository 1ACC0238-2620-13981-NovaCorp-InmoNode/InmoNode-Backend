package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.entities;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;

import java.time.LocalDate;

/**
 * One projected installment of a simulated schedule; it lives inside its {@code Quotation}, identified there by its
 * number.
 *
 * @param amount    what the buyer pays: principal plus interest
 * @param balance   financed balance left after paying it
 */
public record ScheduledInstallment(int number, LocalDate dueDate, Money amount, Money principal, Money interest,
                                   Money balance) {

    public ScheduledInstallment {
        if (number < 1 || dueDate == null || amount == null || principal == null || interest == null
                || balance == null) {
            throw new IllegalArgumentException("an installment needs its number, due date and amounts");
        }
    }
}
