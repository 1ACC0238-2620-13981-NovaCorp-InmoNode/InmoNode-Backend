package com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;

/**
 * Identity claims extracted from a valid token (US-31, Scenario 2).
 */
public record TokenClaims(Long userId, String email, Role role) {
}
