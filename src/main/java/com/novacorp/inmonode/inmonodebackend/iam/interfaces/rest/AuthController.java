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
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.TokenResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.UserResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.VerifyEmailResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.transform.TokenResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.transform.UserResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.resources.ErrorResource;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ErrorResponseAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public operations: none of them takes a token. Every error answers an {@link ErrorResource} with its code.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Account registration, email verification, sign-in and password recovery")
@SecurityRequirements
public class AuthController {

    private final AuthCommandService authCommandService;

    public AuthController(AuthCommandService authCommandService) {
        this.authCommandService = authCommandService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a buyer account (US-14)",
            description = "Creates a BUYER account in status INACTIVE and emails a verification link; the account "
                    + "can sign in only after POST /api/v1/auth/verify-email.")
    @ApiResponse(responseCode = "201", description = "Account created, pending verification",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResource.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: invalid email, or a password outside 8 to 72 characters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "409", description = "USER_CONFLICT: the email is already registered",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    public ResponseEntity<?> register(@Valid @RequestBody RegisterUserResource resource) {
        var result = authCommandService.handle(new RegisterUserCommand(resource.email(), resource.password()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, UserResourceAssembler::toResourceFromEntity, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in and obtain a JWT plus a refresh token (US-01, US-14)",
            description = "The JWT carries the role (BUYER, FIELD_AGENT, CATALOG_ADMIN or FINANCE_ADMIN) in its "
                    + "role claim. The fifth wrong password in a row locks the account for a while.")
    @ApiResponse(responseCode = "200", description = "Signed in",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResource.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: email or password missing",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: unknown email or wrong password",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "403", description = "ACCOUNT_INACTIVE: the email is not verified yet",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED: too many failed attempts; details tells how many minutes to wait",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
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
    @ApiResponse(responseCode = "200", description = "New token pair; the presented refresh token no longer works",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResource.class)))
    @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN: unknown, expired, revoked or already used",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
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
    @Operation(summary = "Activate an account with the emailed token (US-14)",
            description = "The token comes in the link of the verification email and works once.")
    @ApiResponse(responseCode = "200", description = "Account activated (status ACTIVE)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResource.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: unknown, expired or already used token; "
            + "request a new link with POST /api/v1/auth/resend-verification",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailResource resource) {
        var result = authCommandService.handle(new VerifyEmailCommand(resource.token()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, UserResourceAssembler::toResourceFromEntity, HttpStatus.OK);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Email a link to reset a forgotten password (US-14)",
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
    @Operation(summary = "Set a new password with the emailed token (US-14)",
            description = "204 when the password changed. An unknown, expired or already used token answers 400 "
                    + "INVALID_RESET_TOKEN, and a new link must be requested. The change lifts a lock from failed "
                    + "sign-ins and revokes every refresh token of the account; access tokens already issued stay "
                    + "valid until they expire.")
    @ApiResponse(responseCode = "204", description = "The password was changed", content = @Content)
    @ApiResponse(responseCode = "400", description = "INVALID_RESET_TOKEN: unknown, expired or already used token; "
            + "VALIDATION_ERROR: a new password outside 8 to 72 characters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordResource resource) {
        var result = authCommandService.handle(new ResetPasswordCommand(resource.token(), resource.newPassword()));
        return switch (result) {
            case Result.Success<User, ApplicationError> ignored -> ResponseEntity.noContent().build();
            case Result.Failure<User, ApplicationError> failure ->
                    ErrorResponseAssembler.toErrorResponseFromApplicationError(failure.error());
        };
    }
}
