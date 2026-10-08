package com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.hashing;

/**
 * Outbound port for one-way password hashing.
 */
public interface HashingService {

    String encode(CharSequence rawPassword);

    boolean matches(CharSequence rawPassword, String encodedPassword);
}
