package com.novacorp.inmonode.inmonodebackend.iam.domain.services;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RefreshTokenCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RegisterUserCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.ResendVerificationEmailCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignInCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignOutCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.VerifyEmailCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.AuthTokens;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Command side of the identity and access context.
 */
public interface AuthCommandService {

    /** US-14: creates an {@code INACTIVE} buyer account and triggers the verification email. */
    Result<User, ApplicationError> handle(RegisterUserCommand command);

    /** US-01: validates credentials and returns the signed JWT plus a refresh token. */
    Result<AuthTokens, ApplicationError> handle(SignInCommand command);

    /** US-14: activates the account that owns the verification token. */
    Result<User, ApplicationError> handle(VerifyEmailCommand command);

    /**
     * US-14: emails a new verification link to an account still pending verification. Silent: an
     * unknown email, an active account or a request within the cooldown does nothing, so the outcome
     * reveals nothing about the account.
     */
    void handle(ResendVerificationEmailCommand command);

    /**
     * Rotates the refresh token: the presented one is revoked and a new pair is issued. Presenting a
     * token that was already revoked revokes every refresh token of its user.
     */
    Result<AuthTokens, ApplicationError> handle(RefreshTokenCommand command);

    /**
     * Revokes the refresh token. Idempotent and silent: an unknown or already revoked token is
     * ignored, so the outcome reveals nothing about the token.
     */
    void handle(SignOutCommand command);
}
