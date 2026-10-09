package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.queries.*;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.*;
import com.novacorp.inmonode.inmonodebackend.shared.application.documents.PdfGenerator;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.*;
import org.springframework.stereotype.Service;
import java.util.ArrayList;

@Service
public class QuotationDocumentQueryServiceImpl implements QuotationDocumentQueryService {
    private final QuotationQueryService quotations;
    private final PdfGenerator pdf;

    public QuotationDocumentQueryServiceImpl(QuotationQueryService quotations, PdfGenerator pdf) {
        this.quotations = quotations;
        this.pdf = pdf;
    }

    @Override
    public Result<byte[], ApplicationError> handle(DownloadQuotationQuery query) {
        return quotations.handle(new GetQuotationQuery(query.quotationId())).map(quote -> {
            var currency = quote.getLotPrice().currency();
            var lines = new ArrayList<String>();
            lines.add("Cotización: " + quote.getId() + " | Comprador: " + quote.getBuyerId());
            lines.add("Proyecto: " + quote.getProjectId() + " | Lote: " + quote.getLotCode() + " (" + quote.getLotId() + ")");
            lines.add("Precio: " + quote.getLotPrice().amount() + " " + currency);
            lines.add("Inicial: " + quote.getInitialPayment().amount() + " " + currency);
            lines.add("Financiado: " + quote.financedAmount().amount() + " " + currency);
            lines.add("Plazo: " + quote.getTermMonths() + " meses | TEA: " + quote.getAnnualInterestRate() + " %");
            lines.add("Generada: " + quote.getGeneratedAt() + " | Vigente hasta: " + quote.getValidUntil());
            lines.add("Documento de evaluación; las fechas del contrato se calcularán desde su emisión.");
            lines.add("");
            lines.add("N° | Vencimiento | Cuota | Capital | Interés | Saldo (" + currency + ")");
            for (var installment : quote.getInstallments()) {
                lines.add("%d | %s | %s | %s | %s | %s".formatted(installment.number(), installment.dueDate(),
                        installment.amount().amount(), installment.principal().amount(), installment.interest().amount(),
                        installment.balance().amount()));
            }
            lines.add("Interés total: " + quote.totalInterest().amount() + " " + currency);
            lines.add("Total con inicial: " + quote.totalToPay().amount() + " " + currency);
            return pdf.generateReadOnly("Cotización de financiamiento", lines);
        });
    }
}
