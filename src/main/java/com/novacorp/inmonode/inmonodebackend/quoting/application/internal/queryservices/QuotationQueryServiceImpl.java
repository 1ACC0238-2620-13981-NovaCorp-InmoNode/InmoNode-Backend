package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.queries.GetQuotationQuery;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.repositories.QuotationRepository;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.QuotationQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QuotationQueryServiceImpl implements QuotationQueryService {

    private final QuotationRepository quotationRepository;
    private final ExternalIamService externalIamService;

    public QuotationQueryServiceImpl(QuotationRepository quotationRepository, ExternalIamService externalIamService) {
        this.quotationRepository = quotationRepository;
        this.externalIamService = externalIamService;
    }

    @Override
    public Result<Quotation, ApplicationError> handle(GetQuotationQuery query) {
        var buyerId = externalIamService.currentBuyerId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        return quotationRepository.findById(query.quotationId())
                .filter(quotation -> quotation.belongsTo(buyerId))
                .<Result<Quotation, ApplicationError>>map(Result::success)
                .orElseGet(() -> Result.failure(
                        ApplicationError.notFound("quotation", String.valueOf(query.quotationId()))));
    }
}
