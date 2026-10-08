package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Anti-corruption layer towards IAM: who the calling buyer is. Named bean, because other contexts have an ACL class
 * with the same simple name.
 */
@Service("quotingExternalIamService")
public class ExternalIamService {

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    /** The authenticated buyer; empty when the request carries no valid token. */
    public Optional<Long> currentBuyerId() {
        return iamContextFacade.currentUserId();
    }
}
