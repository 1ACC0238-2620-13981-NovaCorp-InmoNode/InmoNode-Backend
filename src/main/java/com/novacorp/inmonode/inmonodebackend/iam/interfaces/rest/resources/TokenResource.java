package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources;

/**
 * @param token        the access token (JWT), sent as {@code Authorization: Bearer <token>}
 * @param expiresIn    seconds until the access token expires
 * @param refreshToken single-use token for {@code POST /api/v1/auth/refresh}; store it securely
 */
public record TokenResource(String token, String tokenType, long expiresIn, String refreshToken) {
}
