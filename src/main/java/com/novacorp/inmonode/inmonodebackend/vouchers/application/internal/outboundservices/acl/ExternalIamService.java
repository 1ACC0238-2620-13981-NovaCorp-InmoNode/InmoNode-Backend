package com.novacorp.inmonode.inmonodebackend.vouchers.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Anti-corruption layer towards IAM: who the caller is, the agent of a field reservation or the buyer of a web
 * separation. Named bean, because other contexts have an ACL class with the same simple name.
 */
@Service("vouchersExternalIamService")
public class ExternalIamService {

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    /** The authenticated user; empty when the request carries no valid token. */
    public Optional<Long> currentUserId() {
        return iamContextFacade.currentUserId();
    }
}
