package com.novacorp.inmonode.inmonodebackend.financial.application.internal.documents;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.shared.application.documents.DocumentFormats;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the preliminary purchase contract of a verified web reservation (US-21) from the server's own data, for the
 * document consumer to render (US-45). Amounts read as in the apps, "S/ 45,000.00"; dates are in Lima.
 *
 * <p>The clause text is a first version for the product owner to review, like the field app's preview.</p>
 */
public final class PreliminaryContractComposer {

    public static final String TITLE = "Contrato preliminar de compraventa";

    /**
     * US-56: the buyer signs through a provider outside Peru's official infrastructure (IOFE), and under Ley 27269
     * such a signature is worth what the parties agree. So the contract states that agreement, how the signature
     * sheet is tied to the issued PDF by its SHA-256, and what proves the signature.
     */
    static final List<String> E_SIGNATURE_CLAUSE = List.of(
            "SÉPTIMA: FIRMA ELECTRÓNICA",
            "Las partes acuerdan que EL COMPRADOR firme con firma electrónica el contrato que LA EMPRESA emita sobre "
                    + "este lote, conforme a la Ley N.° 27269, Ley de Firmas y Certificados Digitales, y a su "
                    + "reglamento. Esa firma no es una firma digital de la Infraestructura Oficial de Firma "
                    + "Electrónica, y las partes la aceptan como medio válido para que EL COMPRADOR manifieste su "
                    + "voluntad y quede obligado por el contrato.",
            "Al emitir el contrato, LA EMPRESA registra la huella SHA-256 de su PDF. EL COMPRADOR firma una hoja de "
                    + "firma que cita el número del contrato, el lote, la fecha de emisión y esa huella, con el "
                    + "enlace que recibe en el portal de compradores y en el correo de su cuenta. Así, la hoja "
                    + "firmada corresponde a ese PDF y a ningún otro.",
            "Las partes aceptan como prueba de la firma la constancia del servicio de firma electrónica, con la fecha "
                    + "y la hora de la firma y el correo del firmante, y el sello electrónico de la hoja firmada. "
                    + "LA EMPRESA conserva el contrato emitido y la hoja firmada.");

    private PreliminaryContractComposer() {
    }

    /**
     * @param buyerEmail the buyer's portal account; {@code null} when IAM no longer knows it
     * @param today      the day the document is generated
     */
    public static DocumentContent compose(Reservation reservation, Lot lot, Project project,
                                          @Nullable String buyerEmail, LocalDate today) {
        var price = lot.getPrice();
        var terms = reservation.getNegotiationTerms();
        var finalPrice = terms.discountAmount() == null ? price
                : new Money(price.amount().subtract(terms.discountAmount()), price.currency());
        var lines = new ArrayList<String>();
        lines.add("Separación: " + reservation.getSourceEventId());
        lines.add("Generado el " + DocumentFormats.date(today) + ".");
        lines.add("");
        lines.add("PARTES");
        var buyer = buyerEmail == null ? "el comprador de esta separación" : "el titular de la cuenta " + buyerEmail
                + " del portal de compradores";
        var coOwner = reservation.getCoOwner() == null ? "" : ", junto con " + reservation.getCoOwner().fullName()
                + ", con " + reservation.getCoOwner().documentType() + " " + reservation.getCoOwner().documentNumber()
                + ", como copropietario";
        lines.add("De una parte, la empresa desarrolladora del proyecto " + project.getName()
                + ", en adelante LA EMPRESA. De la otra parte, " + buyer + coOwner + ", en adelante EL COMPRADOR.");
        lines.add("");
        lines.add("PRIMERA: OBJETO");
        var dimensions = lot.getDimensions();
        var measures = dimensions.front() == null || dimensions.depth() == null ? ""
                : ", " + number(dimensions.front()) + " m de frente y " + number(dimensions.depth()) + " m de fondo";
        lines.add("EL COMPRADOR se compromete a comprar el lote " + lot.getCode() + " (" + lot.getStageName()
                + ") del proyecto " + project.getName() + ", ubicado en " + project.getLocation() + ", con un área de "
                + number(dimensions.area()) + " m²" + measures + ".");
        lines.add("");
        lines.add("SEGUNDA: PRECIO");
        lines.add("El precio de lista del lote es " + money(price) + ".");
        if (terms.discountAmount() != null) {
            lines.add("Descuento autorizado: " + money(new Money(terms.discountAmount(), price.currency()))
                    + ". El precio final pactado es " + money(finalPrice) + ".");
        }
        if (terms.bonus() != null) lines.add("Beneficios adicionales acordados: " + terms.bonus() + ".");
        lines.add("");
        lines.add("TERCERA: SEPARACIÓN");
        lines.add("EL COMPRADOR entregó " + money(reservation.getInitialAmount()) + " como pago inicial el "
                + date(reservation.getReservedAt()) + ", y LA EMPRESA verificó el comprobante el "
                + date(reservation.getVerifiedAt()) + ".");
        lines.add("");
        lines.add("CUARTA: FINANCIAMIENTO");
        var plan = reservation.getFinancingPlan();
        if (plan != null) {
            var balance = new Money(finalPrice.amount().subtract(reservation.getInitialAmount().amount()),
                    finalPrice.currency());
            lines.add("El saldo de " + money(balance) + " se paga en " + plan.termMonths()
                    + " cuotas mensuales con una tasa efectiva anual de " + number(plan.annualInterestRate()) + " %"
                    + (plan.quotationId() == null ? "" : ", según la cotización N.° " + plan.quotationId())
                    + ". El cronograma de cuotas se abre en el estado de cuenta de EL COMPRADOR al emitirse este "
                    + "contrato.");
        } else {
            var rules = project.getFinancingRules();
            lines.add("El saldo se paga según las reglas comerciales vigentes del proyecto: un pago inicial mínimo del "
                    + number(rules.minDownPaymentPercentage()) + " % del precio, una tasa efectiva anual de "
                    + number(rules.annualInterestRate()) + " % y un plazo máximo de " + rules.maxTermMonths()
                    + " meses.");
        }
        lines.add("");
        lines.add("QUINTA: CONDICIONES ADICIONALES");
        lines.add(terms.notes() == null ? "Ninguna." : terms.notes());
        lines.add("");
        lines.add("SEXTA: CONFORMIDAD");
        lines.add("EL COMPRADOR revisa este contrato en el portal de compradores y registra allí su conformidad "
                + "preliminar. La conformidad preliminar no reemplaza la firma electrónica del contrato definitivo.");
        lines.add("");
        lines.addAll(E_SIGNATURE_CLAUSE);
        return new DocumentContent(TITLE, lines);
    }

    private static String money(Money money) {
        return DocumentFormats.money(money.amount(), money.currency());
    }

    private static String number(BigDecimal value) {
        return DocumentFormats.number(value);
    }

    private static String date(@Nullable Instant instant) {
        return instant == null ? "-" : DocumentFormats.date(instant);
    }
}
