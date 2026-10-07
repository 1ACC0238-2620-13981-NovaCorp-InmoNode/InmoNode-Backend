package com.novacorp.inmonode.inmonodebackend.iam.domain.services;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RegisterUserCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignInCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.VerifyEmailCommand;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Command side of the identity and access context.
 */
public interface AuthCommandService {

    /** US-14: creates an {@code INACTIVE} buyer account and triggers the verification email. */
    Result<User, ApplicationError> handle(RegisterUserCommand command);

    /** US-01: validates credentials and returns the signed JWT. */
    Result<String, ApplicationError> handle(SignInCommand command);

    /** US-14: activates the account that owns the verification token. */
    Result<User, ApplicationError> handle(VerifyEmailCommand command);
}
