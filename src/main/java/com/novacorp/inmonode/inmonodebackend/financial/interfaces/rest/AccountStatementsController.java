package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterInstallmentPaymentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetBuyerAccountStatementsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.RegisterInstallmentPaymentResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.AccountStatementResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.BuyerAccountStatementsResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account-statements")
@Tag(name = "Account statements", description = "The buyers' account statements and their installments")
public class AccountStatementsController {

    private final AccountStatementCommandService accountStatementCommandService;
    private final AccountStatementQueryService accountStatementQueryService;

    public AccountStatementsController(AccountStatementCommandService accountStatementCommandService,
                                       AccountStatementQueryService accountStatementQueryService) {
        this.accountStatementCommandService = accountStatementCommandService;
        this.accountStatementQueryService = accountStatementQueryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Get every account statement of the buyer, consolidated (US-27)",
            description = "The caller's lots in one view: per currency, the total invested (down payments and "
                    + "installments paid), the debt (late fees included) and the progress; then each lot, oldest "
                    + "first, with its project, balance, progress, overdue installments, next installment and dueSoon "
                    + "(due within 5 days or late). The portal filters the list to show one lot; its full schedule "
                    + "is at /api/v1/reservations/{transactionId}/account-statement. A buyer who has not agreed to "
                    + "any contract yet gets empty lists.")
    public ResponseEntity<?> getMine() {
        var result = accountStatementQueryService.handle(new GetBuyerAccountStatementsQuery());
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, BuyerAccountStatementsResourceAssembler::toResourceFromStatements, HttpStatus.OK);
    }

    @PostMapping("/{accountStatementId}/installments/{number}/payment")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    @Operation(summary = "Register the payment of an installment (US-23)",
            description = "Finance back office only (FINANCE_ADMIN). Installments are paid whole and in any order, "
                    + "pending or overdue: the amount must be exactly its amountDue (the installment plus its late "
                    + "fee), otherwise 422 BUSINESS_RULE_VIOLATION names the exact amount. paidAt defaults to now "
                    + "and cannot be in the future. Answers the updated statement; with the last installment the "
                    + "balance is 0.00, the progress 100 % and the lot SOLD. 409 INSTALLMENT_CONFLICT when it was "
                    + "already paid; 404 ACCOUNT_STATEMENT_NOT_FOUND or INSTALLMENT_NOT_FOUND.")
    public ResponseEntity<?> registerPayment(@PathVariable Long accountStatementId, @PathVariable int number,
                                             @Valid @RequestBody RegisterInstallmentPaymentResource resource) {
        var result = accountStatementCommandService.handle(new RegisterInstallmentPaymentCommand(accountStatementId,
                number, resource.amount(), resource.paidAt()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, AccountStatementResourceAssembler::toResourceFromView, HttpStatus.OK);
    }
}
