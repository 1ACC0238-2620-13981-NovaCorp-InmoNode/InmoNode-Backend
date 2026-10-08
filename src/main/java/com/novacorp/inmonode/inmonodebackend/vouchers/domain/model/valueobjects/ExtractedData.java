package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;

/**
 * What the field app read from the voucher with OCR (US-08, US-09), as the agent confirmed or corrected it (US-10).
 *
 * @param amount        amount paid, kept at two decimals
 * @param currency      ISO 4217 code, such as PEN or USD
 * @param operationDate day of the bank operation, as printed on the voucher
 * @param operationCode operation number the bank printed on the voucher
 * @param confidence    how sure the OCR was, from 0 to 1; {@code null} when the app did not report it
 */
public record ExtractedData(BigDecimal amount, String currency, LocalDate operationDate, String operationCode,
                            @Nullable BigDecimal confidence) {

    public static final int MAX_OPERATION_CODE_LENGTH = 50;

    public ExtractedData {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        currency = currency == null ? "" : currency.strip().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code such as PEN");
        }
        if (operationDate == null) {
            throw new IllegalArgumentException("operationDate is required");
        }
        operationCode = operationCode == null ? "" : operationCode.strip();
        if (operationCode.isEmpty() || operationCode.length() > MAX_OPERATION_CODE_LENGTH) {
            throw new IllegalArgumentException(
                    "operationCode is required, up to %d characters".formatted(MAX_OPERATION_CODE_LENGTH));
        }
        if (confidence != null && (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        confidence = confidence == null ? null : confidence.setScale(3, RoundingMode.HALF_UP);
    }
}
