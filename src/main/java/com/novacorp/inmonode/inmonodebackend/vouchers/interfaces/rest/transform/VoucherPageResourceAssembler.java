package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources.VoucherHistoryResource;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources.VoucherPageResource;

public final class VoucherPageResourceAssembler {
    private VoucherPageResourceAssembler() { }

    public static VoucherPageResource toResourceFromPage(PageResult<Voucher> page) {
        return new VoucherPageResource(page.items().stream().map(VoucherPageResourceAssembler::toResource).toList(),
                page.totalCount(), page.page(), page.limit());
    }

    private static VoucherHistoryResource toResource(Voucher voucher) {
        var data = voucher.getExtractedData();
        return new VoucherHistoryResource(voucher.getVoucherId(), voucher.getReservationId(), data.amount(),
                data.currency(), data.operationDate(), data.operationCode(), voucher.getContentType().mediaType(),
                voucher.getSizeBytes(), voucher.isManuallyCorrected(), voucher.getStatus().name(), voucher.getReceivedAt());
    }
}
