package com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands;

/**
 * Asks for a new verification email for the account registered with this email.
 */
public record ResendVerificationEmailCommand(String email) {
}
