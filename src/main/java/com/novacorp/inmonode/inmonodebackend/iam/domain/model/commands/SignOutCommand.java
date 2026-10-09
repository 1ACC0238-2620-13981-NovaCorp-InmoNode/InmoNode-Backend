package com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands;

/**
 * Ends the session that owns the refresh token by revoking it.
 */
public record SignOutCommand(String refreshToken) {
}
