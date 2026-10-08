package com.novacorp.inmonode.inmonodebackend.catalog.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Anti-corruption layer towards IAM: who the calling agent is. Named bean, because financial has an ACL class with
 * the same simple name.
 */
@Service("catalogExternalIamService")
public class ExternalIamService {

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    /** The authenticated agent; empty when the request carries no valid token. */
    public Optional<Long> currentAgentId() {
        return iamContextFacade.currentUserId();
    }
}
