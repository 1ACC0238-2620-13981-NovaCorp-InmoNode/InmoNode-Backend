package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherRegistration;
import com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources.VoucherRegistrationResource;

public final class VoucherRegistrationResourceAssembler {

    private VoucherRegistrationResourceAssembler() {}

    public static VoucherRegistrationResource toResourceFromRegistration(VoucherRegistration registration) {
        var voucher = registration.voucher();
        return new VoucherRegistrationResource(voucher.getVoucherId(), voucher.getReservationId(),
                voucher.getStatus().name(), registration.result().name(), voucher.getReceivedAt());
    }
}
