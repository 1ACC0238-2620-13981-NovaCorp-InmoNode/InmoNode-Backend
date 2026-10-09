package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndividualLotPublicationTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final LotRepository lots = mock(LotRepository.class);
    private final LotCommandServiceImpl service = new LotCommandServiceImpl(projects, lots);
    private final FinancingRules rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, new BigDecimal("1.5"));
    private final LotDimensions dimensions = new LotDimensions(new BigDecimal("120"), null, null);
    private final Money price = Money.of(new BigDecimal("45000"));
    private final LotBoundary boundary = LotBoundary.fromPolygonRings(List.of(List.of(
            List.of(-76.70, -12.50), List.of(-76.69, -12.50), List.of(-76.69, -12.49),
            List.of(-76.70, -12.49), List.of(-76.70, -12.50))));

    @Test void stagesRejectDuplicateNamesRegardlessOfCaseAndRemainImmutable() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectStages(List.of(" Norte ", "norte")));
        assertThrows(IllegalArgumentException.class, () -> new ProjectStages(List.of()));
        var names = new ProjectStages(List.of(" Norte ", "Sur")).names();
        assertEquals(List.of("Norte", "Sur"), names);
        assertThrows(UnsupportedOperationException.class, () -> names.add("Otra"));
    }

    @Test void registeringIsDraftAndCannotBeBlockedUntilPublished() {
        var project = Project.withStages(Project.restore(1L, "P", "Lima", null, null, rules, ProjectStatus.DRAFT), List.of("Norte"));
        when(projects.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        when(lots.findCodesByProjectId(1L)).thenReturn(Set.of());
        when(lots.save(any())).thenAnswer(call -> call.getArgument(0));
        var draft = service.handle(new RegisterLotCommand(1L, " norte ", "a-01", dimensions, price, boundary)).toOptional().orElseThrow();
        assertEquals("Norte", draft.getStageName());
        assertEquals(LotStatus.DRAFT, draft.getStatus());
        assertFalse(draft.block(7L, Instant.now(), Duration.ofHours(1)));
        assertTrue(draft.publish());
        assertTrue(draft.block(7L, Instant.now(), Duration.ofHours(1)));
        assertFalse(draft.publish());
        assertEquals(LotStatus.BLOCKED, draft.getStatus());
    }

    @Test void aForeignStageOrDuplicateLotCodeDoesNotWriteAnything() {
        var project = Project.withStages(Project.restore(1L, "P", "Lima", null, null, rules, ProjectStatus.DRAFT), List.of("Norte"));
        when(projects.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        assertTrue(service.handle(new RegisterLotCommand(1L, "Sur", "A-01", dimensions, price, boundary)).toOptional().isEmpty());
        when(lots.findCodesByProjectId(1L)).thenReturn(Set.of("A-01"));
        assertTrue(service.handle(new RegisterLotCommand(1L, "Norte", "a-01", dimensions, price, boundary)).toOptional().isEmpty());
        verify(lots, never()).save(any());
    }

    @Test void anUnpublishedProjectCannotPublishItsLot() {
        var draft = Lot.registerDraft(1L, "Norte", "A-01", dimensions, price, boundary);
        when(lots.findByIdForUpdate(2L)).thenReturn(Optional.of(draft));
        when(projects.findById(1L)).thenReturn(Optional.of(Project.create("P", "Lima", null, null, rules)));
        assertTrue(service.handle(new PublishLotCommand(2L)).toOptional().isEmpty());
        assertEquals(LotStatus.DRAFT, draft.getStatus());
        verify(lots, never()).save(any());
    }
}
