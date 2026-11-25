package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetInstallmentPaymentIntentQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.InstallmentPaymentIntentRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.InstallmentPaymentQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;

/**
 * Answers from what the gateway's notifications already recorded: it never calls the gateway, so the app can ask as
 * often as it needs while it waits for the confirmation.
 */
@Service
public class InstallmentPaymentQueryServiceImpl implements InstallmentPaymentQueryService {

    private final InstallmentPaymentIntentRepository intentRepository;
    private final ExternalIamService externalIamService;

    public InstallmentPaymentQueryServiceImpl(InstallmentPaymentIntentRepository intentRepository,
                                              ExternalIamService externalIamService) {
        this.intentRepository = intentRepository;
        this.externalIamService = externalIamService;
    }

    @Override
    public Result<InstallmentPaymentIntent, ApplicationError> handle(GetInstallmentPaymentIntentQuery query) {
        var buyerId = externalIamService.currentUserId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        // Another buyer's payment is not found, rather than forbidden: its existence is not revealed.
        return intentRepository.findById(query.paymentIntentId())
                .filter(intent -> intent.isFor(query.accountStatementId(), query.installmentNumber()))
                .filter(intent -> intent.belongsTo(buyerId))
                .<Result<InstallmentPaymentIntent, ApplicationError>>map(Result::success)
                .orElseGet(() -> Result.failure(ApplicationError.notFound("payment_intent",
                        query.paymentIntentId().toString())));
    }
}
