package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Outcome of loading a project plan: how many lots were saved and why each of the others was rejected.
 */
public record LotImportResult(int imported, List<RejectedLot> rejected) {

    public LotImportResult {
        rejected = List.copyOf(rejected);
    }

    /**
     * @param index position of the lot in the imported plan, starting at 0
     * @param code  its code as sent, when it had one
     */
    public record RejectedLot(int index, @Nullable String code, String reason) {
    }
}
