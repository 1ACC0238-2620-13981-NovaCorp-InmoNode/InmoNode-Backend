package com.novacorp.inmonode.inmonodebackend.iam.domain.model.events;

/**
 * Raised when a new account is registered and still needs its email verified.
 *
 * @param email             the registered email
 * @param verificationToken the single-use token to send in the verification link
 */
public record UserRegisteredEvent(String email, String verificationToken) {
}
