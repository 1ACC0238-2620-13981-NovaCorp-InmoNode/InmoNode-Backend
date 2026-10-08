package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.constraints.Size;

/**
 * @param note optional remark of the reviewer, up to 500 characters
 */
public record ApprovePaymentEvidenceResource(@Size(max = 500) String note) {
}
