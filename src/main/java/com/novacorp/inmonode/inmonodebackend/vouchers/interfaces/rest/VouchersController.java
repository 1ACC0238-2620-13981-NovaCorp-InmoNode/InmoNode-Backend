package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.services.VoucherCommandService;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources.VoucherUploadRequestResource;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform.RequestVoucherUploadCommandFromResourceAssembler;
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
                    + "that is unknown or belongs to another agent answers 404 RESERVATION_OPERATION_NOT_FOUND.")
    public ResponseEntity<?> requestUploadUrl(@Valid @RequestBody VoucherUploadRequestResource resource) {
        var result = voucherCommandService.handle(
                RequestVoucherUploadCommandFromResourceAssembler.toCommandFromResource(resource));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, VoucherUploadResourceAssembler::toResourceFromUpload, HttpStatus.OK);
    }
}
