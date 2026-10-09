package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.*;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.acl.QuotationSnapshotFacade;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalQuotationSnapshotServiceTest {
    private final QuotationSnapshotFacade quotations = mock(QuotationSnapshotFacade.class);
    private final ExternalQuotationSnapshotService service = new ExternalQuotationSnapshotService(quotations);
    private Reservation reservation() {
        return Reservation.fromWebRequest(3L, 41L, UUID.randomUUID(), Money.of(new BigDecimal("9000")),
                new FinancingPlan(Money.of(new BigDecimal("45000")), 1, new BigDecimal("12"), 15L), Instant.EPOCH);
    }
    @Test void savedAmountsArePreservedAndDatesStartAtIssuanceEvenForHistoricalQuotes() {
        when(quotations.findById(15L)).thenReturn(Optional.of(new QuotationSnapshotFacade.Snapshot(new BigDecimal("45000"),
                new BigDecimal("9000"), "PEN", 1, new BigDecimal("12"),
                List.of(new QuotationSnapshotFacade.Quota(1, new BigDecimal("36000"), new BigDecimal("360"))))));
        var schedule = service.scheduleFor(reservation(), LocalDate.parse("2026-10-09"));
        // Historical quotation used a nominal rate. Its accepted figures must not be recalculated as TEA.
        assertEquals(new BigDecimal("36360.00"), schedule.getFirst().getAmount());
        assertEquals(LocalDate.parse("2026-11-09"), schedule.getFirst().getDueDate());
    }
    @Test void aSnapshotWithDifferentFinancialTermsIsNeverSilentlyUsed() {
        when(quotations.findById(15L)).thenReturn(Optional.of(new QuotationSnapshotFacade.Snapshot(new BigDecimal("50000"),
                new BigDecimal("9000"), "PEN", 1, new BigDecimal("12"), List.of())));
        assertThrows(IllegalStateException.class, () -> service.scheduleFor(reservation(), LocalDate.now()));
    }
}
