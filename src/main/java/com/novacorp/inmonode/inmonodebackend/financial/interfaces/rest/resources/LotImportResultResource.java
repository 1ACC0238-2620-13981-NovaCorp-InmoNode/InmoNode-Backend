package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.util.List;

/**
 * @param imported lots saved
 * @param rejected lots left out, each with its position in the plan (from 0), code and reason
 */
public record LotImportResultResource(int imported, List<RejectedLotResource> rejected) {

    public record RejectedLotResource(int index, String code, String reason) {
    }
}
