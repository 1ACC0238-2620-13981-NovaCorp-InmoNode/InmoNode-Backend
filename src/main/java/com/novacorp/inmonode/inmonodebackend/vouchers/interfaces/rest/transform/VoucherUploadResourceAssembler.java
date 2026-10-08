package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherUpload;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources.VoucherUploadResource;

public final class VoucherUploadResourceAssembler {

    private VoucherUploadResourceAssembler() {}

    public static VoucherUploadResource toResourceFromUpload(VoucherUpload upload) {
        return new VoucherUploadResource(upload.uploadUrl().toString(), upload.objectKey(), upload.expiresAt(),
                upload.headers());
    }
}
