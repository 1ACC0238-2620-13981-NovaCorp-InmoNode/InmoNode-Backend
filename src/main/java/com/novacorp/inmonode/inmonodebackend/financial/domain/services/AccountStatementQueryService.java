package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetBuyerAccountStatementsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationAccountStatementQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.AccountStatementView;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.BuyerAccountStatements;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Query side of the account statements, for the buyers.
 */
public interface AccountStatementQueryService {

    /**
     * US-23: the account statement of a reservation of the caller. Fails with {@code RESERVATION_NOT_FOUND} when the
     * reservation does not exist or is not the caller's, and with {@code ACCOUNT_STATEMENT_NOT_FOUND} while the buyer
     * has not agreed to the contract yet.
     */
    Result<AccountStatementView, ApplicationError> handle(GetReservationAccountStatementQuery query);

    /**
     * US-27: every account statement of the caller, with the totals of all their lots; none yet is an empty view.
     */
    Result<BuyerAccountStatements, ApplicationError> handle(GetBuyerAccountStatementsQuery query);
}
