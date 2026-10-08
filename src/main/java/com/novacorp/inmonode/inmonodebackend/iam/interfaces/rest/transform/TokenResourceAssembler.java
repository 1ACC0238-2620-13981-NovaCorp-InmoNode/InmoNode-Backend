package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.AuthTokens;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.TokenResource;

public final class TokenResourceAssembler {

    private static final String BEARER = "Bearer";

    private TokenResourceAssembler() {}

    public static TokenResource toResourceFromAuthTokens(AuthTokens tokens) {
        return new TokenResource(tokens.accessToken(), BEARER, tokens.expiresInSeconds(), tokens.refreshToken());
    }
}
