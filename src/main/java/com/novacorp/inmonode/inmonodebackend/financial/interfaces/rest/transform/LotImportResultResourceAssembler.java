package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotImportResult;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.LotImportResultResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.LotImportResultResource.RejectedLotResource;

public final class LotImportResultResourceAssembler {

    private LotImportResultResourceAssembler() {}

    public static LotImportResultResource toResourceFromResult(LotImportResult result) {
        var rejected = result.rejected().stream()
                .map(lot -> new RejectedLotResource(lot.index(), lot.code(), lot.reason()))
                .toList();
        return new LotImportResultResource(result.imported(), rejected);
    }
}
