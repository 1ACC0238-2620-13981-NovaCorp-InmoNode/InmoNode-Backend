package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationContractQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractAvailability;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationContract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ContractQueryServiceImpl implements ContractQueryService {

    /** A download link works for 10 minutes, like every other link to a file. */
    static final Duration DOWNLOAD_URL_VALIDITY = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final ObjectStorage objectStorage;
    private final ExternalIamService externalIamService;

    public ContractQueryServiceImpl(ReservationRepository reservationRepository,
                                    ContractRepository contractRepository, ObjectStorage objectStorage,
                                    ExternalIamService externalIamService) {
        this.reservationRepository = reservationRepository;
        this.contractRepository = contractRepository;
        this.objectStorage = objectStorage;
        this.externalIamService = externalIamService;
    }

    @Override
    public Result<ReservationContract, ApplicationError> handle(GetReservationContractQuery query) {
        var buyerId = externalIamService.currentUserId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        var reservation = reservationRepository.findBySourceEventId(query.transactionId())
                .filter(found -> found.getRequesterId().equals(buyerId))
                .orElse(null);
        if (reservation == null) {
            return Result.failure(ApplicationError.notFound("reservation", query.transactionId().toString()));
        }
        var contract = contractRepository.findByReservationId(Objects.requireNonNull(reservation.getId()));
        if (contract.isEmpty()) {
            return Result.success(new ReservationContract(reservation, availabilityWithoutContract(reservation),
                    null, null, null));
        }
        var download = objectStorage.presignDownload(contract.get().getObjectKey(), DOWNLOAD_URL_VALIDITY);
        return Result.success(new ReservationContract(reservation, ContractAvailability.ISSUED, contract.get(),
                download.url(), download.expiresAt()));
    }

    /** US-21, Scenario 2: once the payment arrived, the contract is on its way. */
    private static ContractAvailability availabilityWithoutContract(Reservation reservation) {
        var paid = reservation.getStatus() == ReservationStatus.PENDING_VERIFICATION
                || reservation.getStatus() == ReservationStatus.VERIFIED;
        return paid ? ContractAvailability.IN_PREPARATION : ContractAvailability.NOT_AVAILABLE;
    }
}
