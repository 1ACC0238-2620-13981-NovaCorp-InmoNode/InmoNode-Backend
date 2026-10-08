package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherFile;

/**
 * Asks for a presigned URL to upload the file of a voucher of one of the caller's reservations (US-33).
 */
public record RequestVoucherUploadCommand(VoucherFile file) {
}
