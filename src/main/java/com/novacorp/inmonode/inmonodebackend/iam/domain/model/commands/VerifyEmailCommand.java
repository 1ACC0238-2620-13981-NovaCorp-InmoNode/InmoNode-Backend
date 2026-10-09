package com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands;

/**
 * Activates the account that owns the given verification token.
 */
public record VerifyEmailCommand(String token) {
}
