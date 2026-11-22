package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.documents;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.InitialPayment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.LotSnapshot;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** The quotation PDF reads at a glance: amounts as in the apps, dates in Lima and the schedule as a table. */
class QuotationDocumentContentTest {

    private static final Quotation QUOTE = Quotation.simulate(5L,
            new LotSnapshot(3L, 1L, "A-01", new BigDecimal("120"), new Money(new BigDecimal("45000.00"), "PEN"), true,
                    new FinancingRules(new BigDecimal("20"), new BigDecimal("12.0000"), 120)),
            new InitialPayment(new Money(new BigDecimal("9000.00"), "PEN")), 12,
            Instant.parse("2026-11-22T00:21:46Z"), Duration.ofDays(7));

    @Test
    void amountsAndDatesReadAsInTheApps() {
        var lines = QuotationDocumentContent.lines(QUOTE);

        assertTrue(lines.contains("Precio: S/ 45,000.00"), lines::toString);
        assertTrue(lines.contains("Inicial: S/ 9,000.00"), lines::toString);
        assertTrue(lines.contains("Financiado: S/ 36,000.00"), lines::toString);
        assertTrue(lines.contains("Plazo: 12 meses | TEA: 12 %"), lines::toString);
        assertTrue(lines.contains("Generada: 21/11/2026 19:21 | Vigente hasta: 28/11/2026 19:21"), lines::toString);
        assertTrue(lines.contains("Interés total: S/ 2,258.81"), lines::toString);
        assertTrue(lines.contains("Total con inicial: S/ 47,258.81"), lines::toString);
    }

    @Test
    void theScheduleIsATableWithOneRowPerInstallment() {
        var lines = QuotationDocumentContent.lines(QUOTE);
        var header = lines.indexOf("N°\tVencimiento\tCuota (S/)\tCapital (S/)\tInterés (S/)\tSaldo (S/)");

        assertTrue(header > 0, lines::toString);
        var rows = lines.subList(header + 1, header + 13);
        assertTrue(rows.getFirst().matches("1\t\\d{2}/\\d{2}/\\d{4}\t3,188\\.23\t2,846\\.63\t341\\.60\t33,153\\.37"),
                rows.getFirst());
        assertTrue(rows.getLast().matches("12\t\\d{2}/\\d{2}/\\d{4}\t[\\d,.]+\t[\\d,.]+\t[\\d,.]+\t0\\.00"), rows.getLast());
        assertEquals("", lines.get(header + 13), "the totals come after the table");
    }
}
