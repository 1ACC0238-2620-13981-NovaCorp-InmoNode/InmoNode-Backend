package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;

/**
 * Anti-corruption layer towards IAM: answers, in this context's terms, the questions it has about the caller.
 */
@Service
public class ExternalIamService {

    private static final String CATALOG_ADMIN = "CATALOG_ADMIN";

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    /** Whether the caller manages the catalog, and may therefore see draft projects. */
    public boolean isCatalogAdmin() {
        return iamContextFacade.currentUserRole().filter(CATALOG_ADMIN::equals).isPresent();
    }
}
