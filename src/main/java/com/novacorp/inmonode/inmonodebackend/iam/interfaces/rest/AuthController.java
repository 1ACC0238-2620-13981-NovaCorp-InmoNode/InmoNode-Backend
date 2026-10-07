package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RegisterUserCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignInCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.VerifyEmailCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.services.AuthCommandService;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.RegisterUserResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.SignInResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.TokenResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.VerifyEmailResource;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.transform.UserResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Authentication", description = "Account registration, email verification and sign-in")
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
    @Operation(summary = "Sign in and obtain a JWT (US-01)")
    public ResponseEntity<?> login(@Valid @RequestBody SignInResource resource) {
        var result = authCommandService.handle(new SignInCommand(resource.email(), resource.password()));
        return ResponseEntityAssembler.toResponseEntityFromResult(result, TokenResource::bearer, HttpStatus.OK);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Activate an account with the emailed token (US-14)")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailResource resource) {
        var result = authCommandService.handle(new VerifyEmailCommand(resource.token()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, UserResourceAssembler::toResourceFromEntity, HttpStatus.OK);
    }
}
