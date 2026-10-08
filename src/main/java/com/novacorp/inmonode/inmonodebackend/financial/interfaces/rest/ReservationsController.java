package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationAccountStatementQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationPaymentsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.AccountStatementQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.AccountStatementResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.ReservationPaymentsResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The reservations as their requesters see them, identified by the transaction id they already know.
 */
@RestController
@RequestMapping("/api/v1/reservations/{transactionId}")
@Tag(name = "Reservations", description = "Payments and documents of a reservation, for the person who made it")
public class ReservationsController {

    private final ReservationQueryService reservationQueryService;
    private final AccountStatementQueryService accountStatementQueryService;

    public ReservationsController(ReservationQueryService reservationQueryService,
                                  AccountStatementQueryService accountStatementQueryService) {
        this.reservationQueryService = reservationQueryService;
        this.accountStatementQueryService = accountStatementQueryService;
    }

    @GetMapping("/account-statement")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Get the account statement of a reservation (US-23)",
            description = "The buyer who made the reservation. It is opened when the buyer agrees to the contract: "
                    + "the down payment, the monthly installments (French amortization of the financed balance, "
                    + "due on the same day of each month in Lima), what was paid, the balance and the progress. "
                    + "dueSoon flags a next installment due within 5 days or already late (US-24). Before the "
                    + "agreement it answers 404 ACCOUNT_STATEMENT_NOT_FOUND; a reservation that is unknown or "
                    + "belongs to someone else answers 404 RESERVATION_NOT_FOUND.")
    public ResponseEntity<?> getAccountStatement(@PathVariable UUID transactionId) {
        var result = accountStatementQueryService.handle(new GetReservationAccountStatementQuery(transactionId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, AccountStatementResourceAssembler::toResourceFromView, HttpStatus.OK);
    }

    @GetMapping("/payment-evidences")
    @PreAuthorize("hasAnyRole('BUYER', 'FIELD_AGENT')")
    @Operation(summary = "Get the payment evidences of a reservation (US-25)",
            description = "The buyer (web) or the agent (field) who made the reservation. Approved evidences come "
                    + "with a link to download the voucher, valid for 10 minutes; rejected ones with the reason. "
                    + "After a rejection the reservation is BLOCKED again until waitingUntil: send a substitute "
                    + "voucher with the voucher endpoints and the same reservationId. A reservation that is unknown "
                    + "or belongs to someone else answers 404 RESERVATION_NOT_FOUND.")
    public ResponseEntity<?> getPaymentEvidences(@PathVariable UUID transactionId) {
        var result = reservationQueryService.handle(new GetReservationPaymentsQuery(transactionId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReservationPaymentsResourceAssembler::toResourceFromPayments, HttpStatus.OK);
    }
}
