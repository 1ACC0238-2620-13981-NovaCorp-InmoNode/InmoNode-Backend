package com.novacorp.inmonode.inmonodebackend.vouchers.domain.services;

import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RequestVoucherUploadCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherUpload;

/**
 * Command side of the payment vouchers.
 */
public interface VoucherCommandService {

    /**
     * Signs the upload of a voucher file. Fails with {@code RESERVATION_OPERATION_NOT_FOUND} when the reservation is
     * not an operation of the caller, so someone else's reservation cannot be told apart from a missing one.
     */
    Result<VoucherUpload, ApplicationError> handle(RequestVoucherUploadCommand command);
}
