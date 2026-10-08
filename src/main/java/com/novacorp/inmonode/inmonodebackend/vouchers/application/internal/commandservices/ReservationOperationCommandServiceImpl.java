package com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.ReservationOperation;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RecordFieldReservationCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.ReservationOperationRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.ReservationOperationCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationOperationCommandServiceImpl implements ReservationOperationCommandService {

    private final ReservationOperationRepository operationRepository;

    public ReservationOperationCommandServiceImpl(ReservationOperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    @Override
    @Transactional
    public boolean handle(RecordFieldReservationCommand command) {
        if (operationRepository.existsByReservationId(command.reservationId())) {
            return false;
        }
        operationRepository.save(ReservationOperation.fromFieldReservation(command.reservationId(),
                command.agentId(), command.lotId(), command.initialAmount(), command.reservedAt(),
                command.evidenceDueAt()));
        return true;
    }
}
