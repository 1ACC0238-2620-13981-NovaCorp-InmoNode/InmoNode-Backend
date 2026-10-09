package com.novacorp.inmonode.inmonodebackend.iam.interfaces.acl;

import java.util.Optional;

/**
 * The only entry point other bounded contexts use to learn who the caller is or to look up a user.
 * It exposes plain values, never IAM aggregates or security types.
 */
public interface IamContextFacade {

    /**
     * @return the id of the authenticated caller, or empty when the request carries no valid token
     */
    Optional<Long> currentUserId();

    /**
     * @return the role name of the authenticated caller (e.g. {@code BUYER}), or empty when unauthenticated
     */
    Optional<String> currentUserRole();

    /**
     * @return the email of the user, or empty when no user has that id
     */
    Optional<String> fetchEmailByUserId(Long userId);
}
