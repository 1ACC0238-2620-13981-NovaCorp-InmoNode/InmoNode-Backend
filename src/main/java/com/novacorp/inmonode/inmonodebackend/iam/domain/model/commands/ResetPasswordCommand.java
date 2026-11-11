package com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands;

/**
 * Replaces the password of the account that owns the emailed reset token.
 *
 * @param token       the token received in the reset link
 * @param newPassword the new password, in plain text; it is hashed before reaching the aggregate
 */
public record ResetPasswordCommand(String token, String newPassword) {
}
