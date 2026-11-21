package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage.PresignedDownload;
import org.jspecify.annotations.Nullable;

/**
 * One entry of the verification queue: a pending evidence with the reservation and the lot it pays for.
 *
 * @param identityDocument link to view the photo of the buyer's identity document the field agent attached (US-59);
 *                         {@code null} while it is pending and for a web reservation
 */
public record PendingVerification(PaymentEvidence evidence, Reservation reservation, Lot lot,
                                  @Nullable PresignedDownload identityDocument) {
}
