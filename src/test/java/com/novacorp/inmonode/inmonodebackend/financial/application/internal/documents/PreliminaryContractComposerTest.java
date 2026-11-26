package com.novacorp.inmonode.inmonodebackend.financial.application.internal.documents;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PreliminaryContractComposerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    @Test
    void theContractEndsWithTheBuyersAgreementToSignElectronically() {
        var lines = compose().lines();

        var clause = lines.indexOf("SÉPTIMA: FIRMA ELECTRÓNICA");
        assertTrue(clause > lines.indexOf("SEXTA: CONFORMIDAD"), "the clause follows the preliminary conformity");
        assertEquals(PreliminaryContractComposer.E_SIGNATURE_CLAUSE, lines.subList(clause, lines.size()));
    }

    @Test
    void theClauseNamesTheLawTheHashAndWhatProvesTheSignature() {
        var clause = String.join(" ", PreliminaryContractComposer.E_SIGNATURE_CLAUSE);

        assertTrue(clause.contains("Ley N.° 27269"), clause);
        assertTrue(clause.contains("no es una firma digital de la Infraestructura Oficial de Firma Electrónica"),
                clause);
        assertTrue(clause.contains("huella SHA-256 de su PDF"), clause);
        assertTrue(clause.contains("constancia del servicio de firma electrónica"), clause);
    }

    private static DocumentContent compose() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12.5"), 120, BigDecimal.ONE);
        var project = Project.create("Las Lomas", "Chilca", null, null, rules);
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        var lot = Lot.registerDraft(1L, "Norte", "G-01", new LotDimensions(new BigDecimal("120"), null, null),
                Money.of(new BigDecimal("45000")), boundary);
        var reservation = Reservation.restore(5L, 3L, ReservationChannel.WEB, 41L, null, UUID.randomUUID(),
                Money.of(new BigDecimal("9000")), NOW, ReservationStatus.VERIFIED, List.of(), NOW, null);
        return PreliminaryContractComposer.compose(reservation, lot, project, "buyer41@mail.com",
                LocalDate.of(2026, 11, 20));
    }
}
