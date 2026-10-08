package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a buyer is told about one installment. Amounts are in {@code currency}.
 *
 * @param penalty   late fee, zero until it falls overdue
 * @param amountDue what pays it: the amount plus the late fee
 */
public record PaymentNotice(String email, UUID transactionId, String projectName, String lotCode,
                            int installmentNumber, int termMonths, LocalDate dueDate, BigDecimal amount,
                            BigDecimal penalty, BigDecimal amountDue, String currency) {
}
