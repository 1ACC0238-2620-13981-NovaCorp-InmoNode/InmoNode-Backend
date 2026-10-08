package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.PaymentEvidenceEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ReservationEntity;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Translates between the {@link Reservation} aggregate and its JPA persistence entity.
 */
public final class ReservationEntityAssembler {

    private ReservationEntityAssembler() {}

    public static Reservation toDomain(ReservationEntity entity) {
        return Reservation.restore(entity.getId(), entity.getLotId(), entity.getChannel(), entity.getRequesterId(),
                entity.getProspectId(), entity.getSourceEventId(),
                new Money(entity.getInitialAmount(), entity.getInitialAmountCurrency()),
                entity.getReservedAt(), entity.getStatus(),
                entity.getEvidences().stream().map(ReservationEntityAssembler::toDomain).toList());
    }

    /**
     * Copies the aggregate state onto the entity; audit columns and id stay untouched. Evidences are matched by
     * reference: new ones are added, known ones updated and the ones no longer in the aggregate removed.
     */
    public static ReservationEntity copyToEntity(Reservation reservation, ReservationEntity entity) {
        entity.setLotId(reservation.getLotId());
        entity.setChannel(reservation.getChannel());
        entity.setRequesterId(reservation.getRequesterId());
        entity.setProspectId(reservation.getProspectId());
        entity.setSourceEventId(reservation.getSourceEventId());
        entity.setInitialAmount(reservation.getInitialAmount().amount());
        entity.setInitialAmountCurrency(reservation.getInitialAmount().currency());
        entity.setStatus(reservation.getStatus());
        entity.setReservedAt(reservation.getReservedAt());
        copyEvidencesToEntity(reservation, entity);
        return entity;
    }

    private static void copyEvidencesToEntity(Reservation reservation, ReservationEntity entity) {
        Set<UUID> references = reservation.getEvidences().stream()
                .map(PaymentEvidence::getReference)
                .collect(Collectors.toSet());
        entity.getEvidences().removeIf(evidence -> !references.contains(evidence.getReference()));
        var stored = entity.getEvidences().stream()
                .collect(Collectors.toMap(PaymentEvidenceEntity::getReference, Function.identity()));
        for (var evidence : reservation.getEvidences()) {
            var evidenceEntity = stored.get(evidence.getReference());
            if (evidenceEntity == null) {
                evidenceEntity = new PaymentEvidenceEntity();
                evidenceEntity.setReservation(entity);
                entity.getEvidences().add(evidenceEntity);
            }
            copyToEntity(evidence, evidenceEntity);
        }
    }

    private static PaymentEvidence toDomain(PaymentEvidenceEntity entity) {
        return PaymentEvidence.restore(entity.getId(), entity.getReference(), entity.getSource(),
                new Money(entity.getAmount(), entity.getCurrency()), entity.getOperationDate(),
                entity.getOperationCode(), entity.isManuallyCorrected(), entity.getObjectKey(), entity.getStatus(),
                entity.isLate(), entity.getSubmittedAt());
    }

    private static void copyToEntity(PaymentEvidence evidence, PaymentEvidenceEntity entity) {
        entity.setReference(evidence.getReference());
        entity.setSource(evidence.getSource());
        entity.setAmount(evidence.getAmount().amount());
        entity.setCurrency(evidence.getAmount().currency());
        entity.setOperationDate(evidence.getOperationDate());
        entity.setOperationCode(evidence.getOperationCode());
        entity.setManuallyCorrected(evidence.isManuallyCorrected());
        entity.setObjectKey(evidence.getObjectKey());
        entity.setStatus(evidence.getStatus());
        entity.setLate(evidence.isLate());
        entity.setSubmittedAt(evidence.getSubmittedAt());
    }
}
