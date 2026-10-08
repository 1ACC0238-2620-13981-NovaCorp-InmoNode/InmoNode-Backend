package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RegisterVoucherCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.ExtractedData;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherFile;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources.RegisterVoucherResource;

public final class RegisterVoucherCommandFromResourceAssembler {

    private RegisterVoucherCommandFromResourceAssembler() {}

    /** @throws IllegalArgumentException when the file or the extracted data are not valid (answered as 400) */
    public static RegisterVoucherCommand toCommandFromResource(RegisterVoucherResource resource) {
        var file = VoucherFile.of(resource.reservationId(), resource.voucherId(), resource.contentType(),
                resource.sizeBytes());
        var extractedData = new ExtractedData(resource.amount(), resource.currency(), resource.operationDate(),
                resource.operationCode(), resource.ocrConfidence());
        return new RegisterVoucherCommand(file, extractedData, resource.manuallyCorrected());
    }
}
