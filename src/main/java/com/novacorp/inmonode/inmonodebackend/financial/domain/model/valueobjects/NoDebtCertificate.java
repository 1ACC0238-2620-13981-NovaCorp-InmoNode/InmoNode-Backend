package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * US-26: the proof that the buyer owes nothing more for a lot, to start the notarial deed. It is compiled on request
 * from a paid-off {@link AccountStatement} and never stored: the same statement always gives the same
 * {@linkplain #number() number} and {@linkplain #verificationCode() verification code}, and only the issue date changes.
 * Amounts are in {@code currency}.
 *
 * @param buyerEmail       how IAM identifies the buyer; null when it no longer knows them
 * @param coOwner          the co-owner of the contract (US-28), if any
 * @param installmentsPaid every scheduled installment, without late fees
 * @param totalPaid        the down payment, every installment and the late fees paid
 * @param paidOffAt        the latest installment payment
 */
public record NoDebtCertificate(Long accountStatementId, Long contractId, Long buyerId, @Nullable String buyerEmail,
                                @Nullable CoOwner coOwner, String projectName, String lotCode, String currency,
                                BigDecimal lotPrice, BigDecimal initialPayment, int installmentCount,
                                BigDecimal installmentsPaid, BigDecimal lateFeesPaid, BigDecimal totalPaid,
                                Instant paidOffAt, Instant issuedAt) {

    /**
     * Scenario 1: the certificate of a statement that owes nothing, late fees included.
     *
     * @throws IllegalStateException when anything is still owed (Scenario 2 is answered before, with the balance)
     */
    public static NoDebtCertificate issue(AccountStatement statement, String projectName, String lotCode,
                                          @Nullable String buyerEmail, @Nullable CoOwner coOwner, Instant issuedAt) {
        var paidOffAt = statement.paidOffAt().orElseThrow(() -> new IllegalStateException(
                "the account statement still owes %s %s".formatted(statement.balance(), statement.getCurrency())));
        var id = Objects.requireNonNull(statement.getId(), "the certificate needs a saved account statement");
        var installmentsPaid = statement.paidAmount();
        var lateFeesPaid = statement.paidLateFees();
        return new NoDebtCertificate(id, statement.getContractId(), statement.getBuyerId(), buyerEmail, coOwner,
                projectName, lotCode, statement.getCurrency(), statement.getLotPrice(), statement.getInitialPayment(),
                statement.getInstallments().size(), installmentsPaid, lateFeesPaid,
                statement.getInitialPayment().add(installmentsPaid).add(lateFeesPaid), paidOffAt, issuedAt);
    }

    /** One certificate per account statement: {@code NA-} and its id. */
    public String number() {
        return "NA-%06d".formatted(accountStatementId);
    }

    /**
     * The representative digital signature: the start of a SHA-256 digest of what the certificate attests (statement,
     * contract, buyer, pay-off date and total), as {@code XXXX-XXXX-XXXX-XXXX}.
     */
    public String verificationCode() {
        var attested = String.join("|", accountStatementId.toString(), contractId.toString(), buyerId.toString(),
                paidOffAt.toString(), totalPaid.toPlainString(), currency);
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(attested.getBytes(StandardCharsets.UTF_8));
            var hex = HexFormat.of().withUpperCase().formatHex(digest, 0, 8);
            return String.join("-", hex.substring(0, 4), hex.substring(4, 8), hex.substring(8, 12),
                    hex.substring(12, 16));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
