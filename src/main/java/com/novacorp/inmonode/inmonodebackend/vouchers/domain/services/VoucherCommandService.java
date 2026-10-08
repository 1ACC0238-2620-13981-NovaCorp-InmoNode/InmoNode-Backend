package com.novacorp.inmonode.inmonodebackend.vouchers.domain.services;

import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RegisterVoucherCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands.RequestVoucherUploadCommand;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherRegistration;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherUpload;

/**
 * Command side of the payment vouchers.
 */
public interface VoucherCommandService {

    /**
     * Signs the upload of a voucher file. Fails with {@code RESERVATION_OPERATION_NOT_FOUND} when the reservation is
     * not an operation of the caller, so someone else's reservation cannot be told apart from a missing one, and with
     * {@code VOUCHER_CONFLICT} when the voucher is already registered, since its file is payment evidence now.
     */
    Result<VoucherUpload, ApplicationError> handle(RequestVoucherUploadCommand command);

    /**
     * Registers a voucher whose file is already in the file repository and announces it as "Comprobante de pago
     * recibido". Idempotent by the voucher id: a re-send answers DUPLICATE with the original voucher. Fails with
     * {@code RESERVATION_OPERATION_NOT_FOUND} as above, with {@code VOUCHER_FILE_NOT_UPLOADED} when the file is
     * missing or differs from the declared type or size, and with {@code VOUCHER_CONFLICT} when the voucher id is
     * already registered for another reservation.
     */
    Result<VoucherRegistration, ApplicationError> handle(RegisterVoucherCommand command);
}
