package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.queries.GetMyVouchersQuery;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.VoucherQueryService;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform.VoucherPageResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.VoucherCommandService;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources.RegisterVoucherResource;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources.VoucherUploadRequestResource;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform.RegisterVoucherCommandFromResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform.RequestVoucherUploadCommandFromResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform.VoucherRegistrationResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform.VoucherUploadResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/vouchers")
@Tag(name = "Vouchers", description = "Payment vouchers of the reservations, with their files in the file repository")
public class VouchersController {

    private final VoucherCommandService voucherCommandService;
    private final VoucherQueryService voucherQueryService;

    public VouchersController(VoucherCommandService voucherCommandService, VoucherQueryService voucherQueryService) {
        this.voucherCommandService = voucherCommandService;
        this.voucherQueryService = voucherQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FIELD_AGENT', 'BUYER')")
    @Operation(summary = "List my vouchers with database pagination (US-46)",
            description = "Only the caller's vouchers, newest receivedAt first with an id tie-breaker. "
                    + "page starts at 1; limit defaults to 20 and is capped at 100. Non-positive values return 400. "
                    + "Total-Count contains the count of all the caller's vouchers. An empty page returns 200. "
                    + "status is the receipt synchronization status; review decisions and approved download links "
                    + "are available at /api/v1/reservations/{transactionId}/payment-evidences.")
    public ResponseEntity<?> getMyVouchers(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int limit) {
        var result = voucherQueryService.handle(new GetMyVouchersQuery(new PageRequest(page, limit)));
        var response = ResponseEntityAssembler.toResponseEntityFromResult(
                result, VoucherPageResourceAssembler::toResourceFromPage, HttpStatus.OK);
        if (result instanceof Result.Success<PageResult<Voucher>, ApplicationError> success) {
            return ResponseEntity.status(response.getStatusCode())
                    .header("Total-Count", Long.toString(success.value().totalCount()))
                    .header("Cache-Control", "no-store")
                    .body(response.getBody());
        }
        return response;
    }

    @PostMapping("/upload-url")
    @PreAuthorize("hasAnyRole('FIELD_AGENT', 'BUYER')")
    @Operation(summary = "Get a presigned URL to upload the file of a voucher (US-33, US-20)",
            description = "Field agents (FIELD_AGENT), for a reservation they synchronized and that took its lot, and "
                    + "buyers (BUYER), for a web separation request that blocked its lot (its transactionId is the "
                    + "reservationId). Upload the file with a single PUT to uploadUrl, sending the returned headers exactly as given; "
                    + "the URL is valid for 10 minutes and the storage answers 403 to any other type or size. "
                    + "Accepts image/jpeg, image/png and application/pdf up to 5 MB (400 otherwise). A reservation "
                    + "that is unknown or belongs to someone else answers 404 RESERVATION_OPERATION_NOT_FOUND; a "
                    + "voucher already registered answers 409 VOUCHER_CONFLICT, since its file is payment evidence.")
    public ResponseEntity<?> requestUploadUrl(@Valid @RequestBody VoucherUploadRequestResource resource) {
        var result = voucherCommandService.handle(
                RequestVoucherUploadCommandFromResourceAssembler.toCommandFromResource(resource));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, VoucherUploadResourceAssembler::toResourceFromUpload, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FIELD_AGENT', 'BUYER')")
    @Operation(summary = "Register a voucher whose file was uploaded, with the data read from it (US-20, US-10)",
            description = "Field agents (FIELD_AGENT) and buyers (BUYER), for their own reservation. Send the same "
                    + "voucherId, reservationId, contentType and sizeBytes used to ask for the upload URL, plus the "
                    + "data read from the voucher (with OCR in the field app, typed in the web portal) and whether it "
                    + "was corrected by hand. Answers 201 with status SYNCED and result RECEIVED, or DUPLICATE when the "
                    + "voucher was already registered (idempotent by voucherId; the original is returned). Answers "
                    + "400 VOUCHER_FILE_NOT_UPLOADED when the file is not in the file repository or differs from the "
                    + "declared type or size, 400 VALIDATION_ERROR for invalid data (amount not positive, operation "
                    + "date in the future, missing operation code), 404 RESERVATION_OPERATION_NOT_FOUND for a "
                    + "reservation that is unknown or belongs to someone else, and 409 VOUCHER_CONFLICT when the "
                    + "voucherId is already registered for another reservation. A voucher of a reservation that "
                    + "expired is still registered, as late evidence for the back office.")
    public ResponseEntity<?> registerVoucher(@Valid @RequestBody RegisterVoucherResource resource) {
        var result = voucherCommandService.handle(
                RegisterVoucherCommandFromResourceAssembler.toCommandFromResource(resource));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, VoucherRegistrationResourceAssembler::toResourceFromRegistration, HttpStatus.CREATED);
    }
}
