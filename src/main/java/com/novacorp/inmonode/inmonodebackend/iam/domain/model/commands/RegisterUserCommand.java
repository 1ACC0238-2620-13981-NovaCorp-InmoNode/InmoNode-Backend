package com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands;

/**
 * Registers a new web account. Public registration always creates a {@code BUYER}.
 */
public record RegisterUserCommand(String email, String password) {
}
