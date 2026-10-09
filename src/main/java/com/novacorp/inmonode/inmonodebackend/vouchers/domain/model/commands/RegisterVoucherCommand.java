package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.ExtractedData;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherFile;

/**
 * Registers a voucher of one of the caller's reservations, whose file was already uploaded with a presigned URL
 * (US-20, US-33).
 *
 * @param file              the file as declared when asking for the URL; its key is derived from it again
 * @param manuallyCorrected whether the agent corrected the data the OCR read (US-10, Scenario 2)
 */
public record RegisterVoucherCommand(VoucherFile file, ExtractedData extractedData, boolean manuallyCorrected) {
}
