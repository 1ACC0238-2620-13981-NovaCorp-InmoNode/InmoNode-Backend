package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RefreshTokenCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RegisterUserCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RequestPasswordResetCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.ResendVerificationEmailCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.ResetPasswordCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignInCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignOutCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.VerifyEmailCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.services.AuthCommandService;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.ForgotPasswordResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.RefreshTokenResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.RegisterUserResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.ResendVerificationResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.ResetPasswordResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.SignInResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.VerifyEmailResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.transform.TokenResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.transform.UserResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ErrorResponseAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Account registration, email verification, sign-in and password recovery")
public class AuthController {

    private final AuthCommandService authCommandService;

    public AuthController(AuthCommandService authCommandService) {
        this.authCommandService = authCommandService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a buyer account (US-14)")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterUserResource resource) {
        var result = authCommandService.handle(new RegisterUserCommand(resource.email(), resource.password()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, UserResourceAssembler::toResourceFromEntity, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in and obtain a JWT plus a refresh token (US-01)")
    public ResponseEntity<?> login(@Valid @RequestBody SignInResource resource) {
        var result = authCommandService.handle(new SignInCommand(resource.email(), resource.password()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, TokenResourceAssembler::toResourceFromAuthTokens, HttpStatus.OK);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Email a new verification link (US-14)",
            description = "Always 204, whether or not the email belongs to an account pending verification, "
                    + "so the response reveals nothing. The previous link stops working. Requests for the same "
                    + "email within the cooldown (60 s by default) send nothing.")
    @ApiResponse(responseCode = "204", description = "Accepted; only an account pending verification gets an email")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationResource resource) {
        authCommandService.handle(new ResendVerificationEmailCommand(resource.email()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new JWT and a new refresh token",
            description = "The presented refresh token is single-use. Replaying one that was already used "
                    + "revokes every refresh token of the account.")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenResource resource) {
        var result = authCommandService.handle(new RefreshTokenCommand(resource.refreshToken()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, TokenResourceAssembler::toResourceFromAuthTokens, HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out by revoking the refresh token",
            description = "Always 204, even for an unknown or already revoked token. The access token stays "
                    + "valid until it expires, so the client must discard it too.")
    @ApiResponse(responseCode = "204", description = "The refresh token is revoked, or was already unusable")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenResource resource) {
        authCommandService.handle(new SignOutCommand(resource.refreshToken()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Activate an account with the emailed token (US-14)")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailResource resource) {
        var result = authCommandService.handle(new VerifyEmailCommand(resource.token()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, UserResourceAssembler::toResourceFromEntity, HttpStatus.OK);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Email a link to reset a forgotten password (US-52)",
            description = "Always 204, whether or not the email belongs to an active account, so the response "
                    + "reveals nothing. The link works once and for 30 minutes by default, and the previous link "
                    + "stops working. Requests for the same email within the cooldown (60 s by default) send "
                    + "nothing. Accounts pending verification get nothing: they must verify their email first.")
    @ApiResponse(responseCode = "204", description = "Accepted; only an active account gets an email")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordResource resource) {
        authCommandService.handle(new RequestPasswordResetCommand(resource.email()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password with the emailed token (US-52)",
            description = "204 when the password changed. An unknown, expired or already used token answers 400 "
                    + "INVALID_RESET_TOKEN, and a new link must be requested. The change lifts a lock from failed "
                    + "sign-ins and revokes every refresh token of the account; access tokens already issued stay "
                    + "valid until they expire.")
    @ApiResponse(responseCode = "204", description = "The password was changed")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordResource resource) {
        var result = authCommandService.handle(new ResetPasswordCommand(resource.token(), resource.newPassword()));
        return switch (result) {
            case Result.Success<User, ApplicationError> ignored -> ResponseEntity.noContent().build();
            case Result.Failure<User, ApplicationError> failure ->
                    ErrorResponseAssembler.toErrorResponseFromApplicationError(failure.error());
        };
    }
}
