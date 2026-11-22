package com.novacorp.inmonode.inmonodebackend.shared.application.documents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How the generated documents write amounts and dates, as the apps do: "S/ 45,000.00" (cut, never rounded up, to
 * two decimals) and "12/12/2026", in Lima time.
 */
public final class DocumentFormats {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DocumentFormats() {
    }

    /** "S/ 45,000.00", "US$ 1,200.00"; other currencies keep their code. */
    public static String money(BigDecimal amount, String currency) {
        return currencySymbol(currency) + " " + amount(amount);
    }

    /** "45,000.00": an amount without its currency, e.g. in a table whose header names it. */
    public static String amount(BigDecimal amount) {
        var format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.DOWN);
        return format.format(amount);
    }

    /** "S/" for PEN, "US$" for USD; any other currency keeps its ISO code. */
    public static String currencySymbol(String currency) {
        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "PEN" -> "S/";
            case "USD" -> "US$";
            default -> currency;
        };
    }

    /** "12.5" for 12.5000: rates and measures without trailing zeros. */
    public static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public static String date(LocalDate date) {
        return DATE.format(date);
    }

    /** The day in Lima. */
    public static String date(Instant instant) {
        return DATE.format(instant.atZone(LIMA));
    }

    /** "12/12/2026 18:30", in Lima. */
    public static String dateTime(Instant instant) {
        return DATE_TIME.format(instant.atZone(LIMA));
    }
}
