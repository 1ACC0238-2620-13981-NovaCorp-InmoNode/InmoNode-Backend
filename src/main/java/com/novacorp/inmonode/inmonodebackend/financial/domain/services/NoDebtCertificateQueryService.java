package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetNoDebtCertificateQuery;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * The documents of the account statements, for the buyers.
 */
public interface NoDebtCertificateQueryService {

    /**
     * US-26: the no-debt certificate of an account statement of the caller, as a read-only PDF. Fails with
     * {@code ACCOUNT_STATEMENT_NOT_FOUND} when it does not exist or is not the caller's, and with
     * {@code BUSINESS_RULE_VIOLATION}, naming the exact balance, while anything is owed.
     */
    Result<byte[], ApplicationError> handle(GetNoDebtCertificateQuery query);
}
