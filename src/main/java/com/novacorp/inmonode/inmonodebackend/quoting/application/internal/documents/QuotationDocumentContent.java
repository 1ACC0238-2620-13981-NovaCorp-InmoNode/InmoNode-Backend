package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.documents;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;

import java.util.ArrayList;
import java.util.List;

import static com.novacorp.inmonode.inmonodebackend.shared.application.documents.DocumentFormats.*;

/**
 * What the PDF of a quotation says (US-18), whether it is downloaded at once or generated in the background (US-45).
 * The schedule is a table (one tab between cells), so its columns line up; amounts read "S/ 45,000.00" and dates are
 * in Lima.
 */
public final class QuotationDocumentContent {

    public static final String TITLE = "Cotización de financiamiento";

    private QuotationDocumentContent() {
    }

    public static List<String> lines(Quotation quote) {
        var symbol = currencySymbol(quote.getLotPrice().currency());
        var lines = new ArrayList<String>();
        lines.add("Cotización: " + quote.getId() + " | Comprador: " + quote.getBuyerId());
        lines.add("Proyecto: " + quote.getProjectId() + " | Lote: " + quote.getLotCode() + " (" + quote.getLotId() + ")");
        lines.add("Precio: " + written(quote.getLotPrice()));
        lines.add("Inicial: " + written(quote.getInitialPayment()));
        lines.add("Financiado: " + written(quote.financedAmount()));
        lines.add("Plazo: " + quote.getTermMonths() + " meses | TEA: " + number(quote.getAnnualInterestRate()) + " %");
        lines.add("Generada: " + dateTime(quote.getGeneratedAt()) + " | Vigente hasta: " + dateTime(quote.getValidUntil()));
        lines.add("Documento de evaluación; las fechas del contrato se calcularán desde su emisión.");
        lines.add("");
        lines.add(String.join("\t", "N°", "Vencimiento", "Cuota (" + symbol + ")", "Capital (" + symbol + ")",
                "Interés (" + symbol + ")", "Saldo (" + symbol + ")"));
        for (var installment : quote.getInstallments()) {
            lines.add(String.join("\t", String.valueOf(installment.number()), date(installment.dueDate()),
                    amount(installment.amount().amount()), amount(installment.principal().amount()),
                    amount(installment.interest().amount()), amount(installment.balance().amount())));
        }
        lines.add("");
        lines.add("Interés total: " + written(quote.totalInterest()));
        lines.add("Total con inicial: " + written(quote.totalToPay()));
        return lines;
    }

    /** "S/ 45,000.00". */
    private static String written(Money money) {
        return money(money.amount(), money.currency());
    }
}
