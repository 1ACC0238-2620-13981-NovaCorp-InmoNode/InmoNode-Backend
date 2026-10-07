package com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands;

/**
 * Authenticates a user with email and password.
 */
public record SignInCommand(String email, String password) {
}
