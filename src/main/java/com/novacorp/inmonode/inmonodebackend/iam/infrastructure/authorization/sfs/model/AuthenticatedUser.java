package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.model;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;

import java.security.Principal;

/**
 * Identity injected into the security context once the JWT is validated (US-31, Scenario 2).
 * Business modules read the caller from here, never from the request payload.
 */
public record AuthenticatedUser(Long userId, String email, Role role) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
