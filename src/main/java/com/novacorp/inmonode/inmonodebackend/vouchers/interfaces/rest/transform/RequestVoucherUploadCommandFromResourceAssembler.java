package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RequestVoucherUploadCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherFile;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources.VoucherUploadRequestResource;

public final class RequestVoucherUploadCommandFromResourceAssembler {

    private RequestVoucherUploadCommandFromResourceAssembler() {}

    /** @throws IllegalArgumentException when the type is not accepted or the size is over 5 MB (answered as 400) */
    public static RequestVoucherUploadCommand toCommandFromResource(VoucherUploadRequestResource resource) {
        return new RequestVoucherUploadCommand(VoucherFile.of(resource.reservationId(), resource.voucherId(),
                resource.contentType(), resource.sizeBytes()));
    }
}
