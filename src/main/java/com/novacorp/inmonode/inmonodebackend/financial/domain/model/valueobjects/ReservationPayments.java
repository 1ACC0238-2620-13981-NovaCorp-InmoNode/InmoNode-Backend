package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * A reservation with one page of its payment evidences, as its requester sees them (US-25, US-46).
 *
 * @param waitingUntil until when the lot waits for a (substitute) voucher; {@code null} when it does not wait
 * @param evidences    the requested page, oldest first, and the count of every evidence of the reservation
 */
public record ReservationPayments(Reservation reservation, @Nullable Instant waitingUntil,
                                  PageResult<EvidenceView> evidences) {

    public ReservationPayments {
        Objects.requireNonNull(evidences, "evidences");
    }

    /**
     * @param downloadUrl       presigned link to the voucher file, only for an approved evidence (US-25, Scenario 1)
     * @param downloadExpiresAt when that link stops working
     */
    public record EvidenceView(PaymentEvidence evidence, @Nullable URI downloadUrl,
                               @Nullable Instant downloadExpiresAt) {
    }
}
