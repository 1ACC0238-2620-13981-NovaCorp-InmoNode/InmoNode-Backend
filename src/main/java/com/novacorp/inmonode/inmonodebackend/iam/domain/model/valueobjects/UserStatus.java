package com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects;

/**
 * Lifecycle status of a user account. New web accounts stay {@code INACTIVE} until the email is verified.
 */
public enum UserStatus {
    INACTIVE,
    ACTIVE
}
