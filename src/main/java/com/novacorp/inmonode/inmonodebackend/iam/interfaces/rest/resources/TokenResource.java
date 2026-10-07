package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources;

public record TokenResource(String token, String tokenType) {

    public static TokenResource bearer(String token) {
        return new TokenResource(token, "Bearer");
    }
}
