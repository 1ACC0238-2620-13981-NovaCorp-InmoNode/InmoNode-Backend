package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
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

@RestController
@RequestMapping("/api/v1/vouchers")
@Tag(name = "Vouchers", description = "Payment vouchers of the reservations, with their files in the file repository")
public class VouchersController {

    private final VoucherCommandService voucherCommandService;

    public VouchersController(VoucherCommandService voucherCommandService) {
        this.voucherCommandService = voucherCommandService;
    }

    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('FIELD_AGENT')")
    @Operation(summary = "Get a presigned URL to upload the file of a voucher (US-33)",
            description = "Field agents only (FIELD_AGENT), for a reservation they synchronized and that took its lot. "
                    + "Upload the file with a single PUT to uploadUrl, sending the returned headers exactly as given; "
                    + "the URL is valid for 10 minutes and the storage answers 403 to any other type or size. "
                    + "Accepts image/jpeg, image/png and application/pdf up to 5 MB (400 otherwise). A reservation "
                    + "that is unknown or belongs to another agent answers 404 RESERVATION_OPERATION_NOT_FOUND; a "
                    + "voucher already registered answers 409 VOUCHER_CONFLICT, since its file is payment evidence.")
    public ResponseEntity<?> requestUploadUrl(@Valid @RequestBody VoucherUploadRequestResource resource) {
        var result = voucherCommandService.handle(
                RequestVoucherUploadCommandFromResourceAssembler.toCommandFromResource(resource));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, VoucherUploadResourceAssembler::toResourceFromUpload, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasRole('FIELD_AGENT')")
    @Operation(summary = "Register a voucher whose file was uploaded, with the data read from it (US-20, US-10)",
            description = "Field agents only (FIELD_AGENT). Send the same voucherId, reservationId, contentType and "
                    + "sizeBytes used to ask for the upload URL, plus the data the app read with OCR and whether the "
                    + "agent corrected it. Answers 201 with status SYNCED and result RECEIVED, or DUPLICATE when the "
                    + "voucher was already registered (idempotent by voucherId; the original is returned). Answers "
                    + "400 VOUCHER_FILE_NOT_UPLOADED when the file is not in the file repository or differs from the "
                    + "declared type or size, 400 VALIDATION_ERROR for invalid data (amount not positive, operation "
                    + "date in the future, missing operation code), 404 RESERVATION_OPERATION_NOT_FOUND for a "
                    + "reservation that is unknown or belongs to another agent, and 409 VOUCHER_CONFLICT when the "
                    + "voucherId is already registered for another reservation. A voucher of a reservation that "
                    + "expired is still registered, as late evidence for the back office.")
    public ResponseEntity<?> registerVoucher(@Valid @RequestBody RegisterVoucherResource resource) {
        var result = voucherCommandService.handle(
                RegisterVoucherCommandFromResourceAssembler.toCommandFromResource(resource));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, VoucherRegistrationResourceAssembler::toResourceFromRegistration, HttpStatus.CREATED);
    }
}
