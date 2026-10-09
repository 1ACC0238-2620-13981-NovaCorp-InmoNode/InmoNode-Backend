package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.queries;

import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;

public record GetMyVouchersQuery(PageRequest pagination) { }
