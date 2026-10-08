package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterInstallmentPaymentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.RegisterInstallmentPaymentResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.AccountStatementResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account-statements")
@Tag(name = "Account statements", description = "Installments of the buyers' account statements")
public class AccountStatementsController {

    private final AccountStatementCommandService accountStatementCommandService;

    public AccountStatementsController(AccountStatementCommandService accountStatementCommandService) {
        this.accountStatementCommandService = accountStatementCommandService;
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
