package com.novacorp.inmonode.inmonodebackend.iam.domain.model.events;

/**
 * Raised when an account still pending verification gets a new verification token; the previous
 * token no longer works.
 *
 * @param email             the email of the account
 * @param verificationToken the new single-use token to send in the verification link
 */
public record VerificationTokenRenewedEvent(String email, String verificationToken) {
}
