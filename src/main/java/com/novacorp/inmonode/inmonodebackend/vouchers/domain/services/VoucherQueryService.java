package com.novacorp.inmonode.inmonodebackend.vouchers.domain.services;

import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.queries.GetMyVouchersQuery;

public interface VoucherQueryService {
    /** Only the caller's receipts, newest first, with a stable id tie-breaker. */
    Result<PageResult<Voucher>, ApplicationError> handle(GetMyVouchersQuery query);
}
