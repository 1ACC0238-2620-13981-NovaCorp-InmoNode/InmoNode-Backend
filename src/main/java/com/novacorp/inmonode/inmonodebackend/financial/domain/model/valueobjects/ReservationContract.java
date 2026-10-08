package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Instant;

/**
 * The contract of a reservation as its buyer sees it (US-21).
 *
 * @param contract          the issued contract; {@code null} unless {@code ISSUED}
 * @param downloadUrl       presigned link to the PDF; {@code null} unless {@code ISSUED}
 * @param downloadExpiresAt when that link stops working
 */
public record ReservationContract(Reservation reservation, ContractAvailability availability,
                                  @Nullable Contract contract, @Nullable URI downloadUrl,
                                  @Nullable Instant downloadExpiresAt) {
}
