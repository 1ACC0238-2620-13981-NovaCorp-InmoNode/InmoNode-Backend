package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources;

import java.util.List;

public record VoucherPageResource(List<VoucherHistoryResource> items, long totalCount, int page, int limit) { }
