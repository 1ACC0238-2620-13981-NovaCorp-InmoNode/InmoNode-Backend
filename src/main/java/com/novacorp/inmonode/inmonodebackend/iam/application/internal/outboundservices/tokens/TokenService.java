package com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;

import java.util.Optional;

/**
 * Outbound port for issuing and validating access tokens.
 */
public interface TokenService {

    /** Issues a signed token carrying the user id, email and role as claims. */
    String generateToken(User user);

    /**
     * Validates signature and expiration.
     *
     * @return the claims when the token is valid, empty when it is malformed, tampered with or expired
     */
    Optional<TokenClaims> parseToken(String token);
}
