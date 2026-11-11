package com.novacorp.inmonode.inmonodebackend.iam.domain.model.events;

/**
 * Raised when an active account gets a new password reset link (US-52, Scenario 1); the previous link no
 * longer works.
 *
 * @param email      the email of the account
 * @param resetToken the single-use token to send in the link. Only its digest is persisted; the token itself
 *                   lives only in this event until the email is sent.
 */
public record PasswordResetRequestedEvent(String email, String resetToken) {
}
