package com.novacorp.inmonode.inmonodebackend.iam.domain.model.events;

/**
 * Raised when the password of an account was replaced through an emailed reset link (US-52).
 *
 * @param email the email of the account
 */
public record PasswordResetEvent(String email) {
}
