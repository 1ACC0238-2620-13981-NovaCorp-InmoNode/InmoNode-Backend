package com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.queries.GetMyVouchersQuery;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.VoucherRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.VoucherQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoucherQueryServiceImpl implements VoucherQueryService {

    private final VoucherRepository voucherRepository;
    private final ExternalIamService externalIamService;

    public VoucherQueryServiceImpl(VoucherRepository voucherRepository, ExternalIamService externalIamService) {
        this.voucherRepository = voucherRepository;
        this.externalIamService = externalIamService;
    }

    @Override
    public Result<PageResult<Voucher>, ApplicationError> handle(GetMyVouchersQuery query) {
        var userId = externalIamService.currentUserId().orElse(null);
        if (userId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The user is not authenticated"));
        }
        return Result.success(voucherRepository.findByOwnerId(userId, query.pagination()));
    }
}
