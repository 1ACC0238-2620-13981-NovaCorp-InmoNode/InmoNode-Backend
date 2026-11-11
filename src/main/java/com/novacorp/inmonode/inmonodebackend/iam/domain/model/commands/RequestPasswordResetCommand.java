package com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands;

/**
 * Asks for a link to reset the password of the account registered with this email.
 */
public record RequestPasswordResetCommand(String email) {
}
