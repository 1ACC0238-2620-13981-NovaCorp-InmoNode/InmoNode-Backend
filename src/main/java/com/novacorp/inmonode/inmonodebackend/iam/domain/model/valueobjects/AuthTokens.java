package com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects;

/**
 * Credentials issued by a successful sign-in or refresh: the short-lived access token (JWT) and the
 * plain refresh token that renews it. The plain refresh token exists only here; it is stored hashed.
 */
public record AuthTokens(String accessToken, long expiresInSeconds, String refreshToken) {
}
