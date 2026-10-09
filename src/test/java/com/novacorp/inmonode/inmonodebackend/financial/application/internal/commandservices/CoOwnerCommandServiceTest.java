package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AddCoOwnerCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoOwnerCommandServiceTest {
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final LotRepository lots = mock(LotRepository.class);
    private final ExternalIamService iam = mock(ExternalIamService.class);
    private final CoOwnerCommandServiceImpl service = new CoOwnerCommandServiceImpl(reservations, contracts, lots, iam);
    private final UUID transaction = UUID.randomUUID();
    private final CoOwner coOwner = new CoOwner("María Pérez", "DNI", "12345678");

    private Reservation reservation(Long userId) {
        return Reservation.restore(5L, 3L, ReservationChannel.WEB, userId, null, transaction,
                Money.of(new BigDecimal("9000")), Instant.EPOCH, ReservationStatus.VERIFIED, List.of(), Instant.EPOCH,
                new FinancingPlan(Money.of(new BigDecimal("45000")), 12, new BigDecimal("12")));
    }

    @Test void ownsTheReservationAndReadsFreshStateUnderTheLotLock() {
        var reservation = reservation(41L);
        when(iam.currentUserId()).thenReturn(Optional.of(41L));
        when(reservations.findBySourceEventId(transaction)).thenReturn(Optional.of(reservation));
        when(reservations.findByIdForUpdate(5L)).thenReturn(Optional.of(reservation));
        when(lots.findByIdForUpdate(3L)).thenReturn(Optional.of(mock(Lot.class)));
        assertEquals(coOwner, service.handle(new AddCoOwnerCommand(transaction, coOwner)).toOptional().orElseThrow());
        var order = inOrder(lots, reservations, contracts);
        order.verify(reservations).findBySourceEventId(transaction);
        order.verify(lots).findByIdForUpdate(3L);
        order.verify(reservations).findByIdForUpdate(5L);
        order.verify(contracts).findByReservationId(5L);
        order.verify(reservations).save(reservation);
        assertEquals(coOwner, reservation.getCoOwner());
    }

    @Test void anotherBuyersReservationIsHiddenWithoutLockingOrWriting() {
        when(iam.currentUserId()).thenReturn(Optional.of(42L));
        when(reservations.findBySourceEventId(transaction)).thenReturn(Optional.of(reservation(41L)));
        assertFalse(service.handle(new AddCoOwnerCommand(transaction, coOwner)).isSuccess());
        verifyNoInteractions(lots, contracts);
        verify(reservations, never()).save(any());
    }

    @Test void anIssuedContractPreventsAnyCoOwnerChange() {
        var reservation = reservation(41L);
        when(iam.currentUserId()).thenReturn(Optional.of(41L));
        when(reservations.findBySourceEventId(transaction)).thenReturn(Optional.of(reservation));
        when(reservations.findByIdForUpdate(5L)).thenReturn(Optional.of(reservation));
        when(lots.findByIdForUpdate(3L)).thenReturn(Optional.of(mock(Lot.class)));
        when(contracts.findByReservationId(5L)).thenReturn(Optional.of(mock(Contract.class)));
        var result = service.handle(new AddCoOwnerCommand(transaction, coOwner));
        assertFalse(result.isSuccess());
        assertNull(reservation.getCoOwner());
        verify(reservations, never()).save(any());
    }

    @Test void issuanceKeepsTheLegalSnapshotAndPersistenceRoundTripsIt() {
        var reservation = reservation(41L);
        reservation.addCoOwner(coOwner);
        var contract = Contract.issue(reservation, new ContractDocument(transaction, UUID.randomUUID(), 2048), 77L, Instant.EPOCH);
        reservation.addCoOwner(new CoOwner("Otra persona", "DNI", "87654321"));
        assertEquals(coOwner, contract.getCoOwner());
        var entity = new com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ContractEntity();
        entity.setId(8L);
        var assembler = com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.ContractEntityAssembler.copyToEntity(contract, entity);
        assertEquals(coOwner, com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.ContractEntityAssembler.toDomain(assembler).getCoOwner());
    }

    @Test void invalidIdentityAndInactiveReservationAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CoOwner("A", "DNI", "123"));
        assertThrows(IllegalArgumentException.class, () -> new CoOwner(" ", "DNI", "12345678"));
        assertThrows(IllegalArgumentException.class, () -> new CoOwner("A", "UNKNOWN", "12345678"));
        var reservation = Reservation.fromWebRequest(3L, 41L, transaction, Money.of(new BigDecimal("9000")),
                new FinancingPlan(Money.of(new BigDecimal("45000")), 12, BigDecimal.ZERO), Instant.EPOCH);
        reservation.expire();
        assertThrows(IllegalArgumentException.class, () -> reservation.addCoOwner(coOwner));
    }
}
