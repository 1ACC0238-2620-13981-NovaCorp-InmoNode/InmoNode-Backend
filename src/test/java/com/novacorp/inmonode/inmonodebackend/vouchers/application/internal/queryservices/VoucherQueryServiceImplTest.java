package com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.queries.GetMyVouchersQuery;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.VoucherRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoucherQueryServiceImplTest {
    private final VoucherRepository repository = mock(VoucherRepository.class);
    private final ExternalIamService iam = mock(ExternalIamService.class);
    private final VoucherQueryServiceImpl service = new VoucherQueryServiceImpl(repository, iam);

    @Test
    void ownerComesFromAuthenticationRatherThanTheQuery() {
        var pagination = new PageRequest(2, 20);
        when(iam.currentUserId()).thenReturn(Optional.of(42L));
        when(repository.findByOwnerId(42L, pagination)).thenReturn(new PageResult<>(List.of(), 0, 2, 20));
        assertTrue(service.handle(new GetMyVouchersQuery(pagination)).isSuccess());
        verify(repository).findByOwnerId(42L, pagination);
    }

    @Test
    void noAuthenticationNeverQueriesReceiptStorage() {
        when(iam.currentUserId()).thenReturn(Optional.empty());
        assertTrue(service.handle(new GetMyVouchersQuery(new PageRequest(1, 20))).isFailure());
        verifyNoInteractions(repository);
    }
}
