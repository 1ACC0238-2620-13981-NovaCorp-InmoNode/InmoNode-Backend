package com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands;

/**
 * Exchanges a refresh token for a new access token and a new (rotated) refresh token.
 */
public record RefreshTokenCommand(String refreshToken) {
}
