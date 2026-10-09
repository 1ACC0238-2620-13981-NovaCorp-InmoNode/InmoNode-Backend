package com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens;

/**
 * Outbound port for creating opaque refresh tokens and deriving the value that is stored for them.
 */
public interface RefreshTokenGenerator {

    /** A new unguessable token, handed to the client once and never stored as is. */
    String generate();

    /**
     * Deterministic digest of a token: what is persisted and what a presented token is looked up by.
     */
    String hash(String token);
}
