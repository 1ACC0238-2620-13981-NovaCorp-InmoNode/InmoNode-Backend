package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AddCoOwnerCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.CoOwner;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.*;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.CoOwnerCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoOwnerCommandServiceImpl implements CoOwnerCommandService {
    private final ReservationRepository reservations;
    private final ContractRepository contracts;
    private final LotRepository lots;
    private final ExternalIamService iam;

    public CoOwnerCommandServiceImpl(ReservationRepository reservations, ContractRepository contracts,
                                    LotRepository lots, ExternalIamService iam) {
        this.reservations = reservations;
        this.contracts = contracts;
        this.lots = lots;
        this.iam = iam;
    }

    @Override
    @Transactional
    public Result<CoOwner, ApplicationError> handle(AddCoOwnerCommand command) {
        var userId = iam.currentUserId().orElse(null);
        if (userId == null) return Result.failure(new ApplicationError("UNAUTHORIZED", "Authentication required"));
        var reservation = reservations.findBySourceEventId(command.transactionId())
                .filter(r -> r.getChannel() == ReservationChannel.WEB && r.getRequesterId().equals(userId))
                .orElse(null);
        if (reservation == null) return Result.failure(ApplicationError.notFound("reservation", command.transactionId().toString()));
        // Issuance uses this same lock. Read the reservation again after acquiring it.
        lots.findByIdForUpdate(reservation.getLotId()).orElseThrow();
        reservation = reservations.findByIdForUpdate(reservation.getId()).orElseThrow();
        if (contracts.findByReservationId(reservation.getId()).isPresent()) {
            return Result.failure(ApplicationError.conflict("co-owner",
                    "The contract has already been issued; contact customer service for a legal addendum"));
        }
        reservation.addCoOwner(command.coOwner());
        reservations.save(reservation);
        return Result.success(command.coOwner());
    }
}
