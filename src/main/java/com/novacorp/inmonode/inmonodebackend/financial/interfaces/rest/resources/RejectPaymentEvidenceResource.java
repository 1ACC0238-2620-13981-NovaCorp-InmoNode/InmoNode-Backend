package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason why it is rejected (e.g. illegible, wrong account); the requester sees it. Up to 500 characters
 */
public record RejectPaymentEvidenceResource(@NotBlank @Size(max = 500) String reason) {
}
