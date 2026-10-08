package com.novacorp.inmonode.inmonodebackend.iam.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.hashing.HashingService;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.RefreshTokenGenerator;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.RefreshToken;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RefreshTokenCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RegisterUserCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignInCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignOutCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.VerifyEmailCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.AuthTokens;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.RefreshTokenRepository;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
import com.novacorp.inmonode.inmonodebackend.iam.domain.services.AuthCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class AuthCommandServiceImpl implements AuthCommandService {

    static final ApplicationError INVALID_CREDENTIALS =
            new ApplicationError("INVALID_CREDENTIALS", "Invalid email or password");
    static final ApplicationError INVALID_REFRESH_TOKEN = new ApplicationError("INVALID_REFRESH_TOKEN",
            "The refresh token is invalid, expired or revoked", "Sign in again");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final Duration refreshTokenTimeToLive;
    private final Clock clock;

    public AuthCommandServiceImpl(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                                  HashingService hashingService, TokenService tokenService,
                                  RefreshTokenGenerator refreshTokenGenerator,
                                  @Value("${authorization.refresh-token.expiration-days:30}") long refreshTokenDays,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenTimeToLive = Duration.ofDays(refreshTokenDays);
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result<User, ApplicationError> handle(RegisterUserCommand command) {
        var email = User.normalize(command.email());
        if (userRepository.existsByEmail(email)) {
            return Result.failure(ApplicationError.conflict("user",
                    "The email %s is already registered; use the password recovery flow".formatted(email)));
        }
        var user = User.register(email, hashingService.encode(command.password()), Role.BUYER);
        return Result.success(userRepository.save(user));
    }

    @Override
    @Transactional
    public Result<AuthTokens, ApplicationError> handle(SignInCommand command) {
        var now = clock.instant();
        var user = userRepository.findByEmail(User.normalize(command.email())).orElse(null);
        if (user == null) {
            return Result.failure(INVALID_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            return Result.failure(accountLocked());
        }
        if (!hashingService.matches(command.password(), user.getPasswordHash())) {
            user.recordFailedAttempt(now);
            userRepository.save(user);
            return Result.failure(user.isLocked(now) ? accountLocked() : INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            return Result.failure(new ApplicationError("ACCOUNT_INACTIVE",
                    "The account is not active", "Verify your email before signing in"));
        }
        user.recordSuccessfulSignIn();
        userRepository.save(user);
        return Result.success(issueTokens(user, now));
    }

    @Override
    @Transactional
    public Result<User, ApplicationError> handle(VerifyEmailCommand command) {
        var user = userRepository.findByVerificationToken(command.token()).orElse(null);
        if (user == null || !user.verifyEmail(command.token())) {
            return Result.failure(ApplicationError.validationError("token",
                    "The verification token is invalid or was already used"));
        }
        return Result.success(userRepository.save(user));
    }

    @Override
    @Transactional
    public Result<AuthTokens, ApplicationError> handle(RefreshTokenCommand command) {
        var now = clock.instant();
        var presented = refreshTokenRepository
                .findByTokenHash(refreshTokenGenerator.hash(command.refreshToken())).orElse(null);
        if (presented == null) {
            return Result.failure(INVALID_REFRESH_TOKEN);
        }
        if (presented.isRevoked()) {
            // An already rotated token is being replayed: whoever holds it may have stolen it.
            refreshTokenRepository.revokeAllActiveByUserId(presented.getUserId(), now);
            return Result.failure(INVALID_REFRESH_TOKEN);
        }
        if (!presented.isUsable(now)) {
            return Result.failure(INVALID_REFRESH_TOKEN);
        }
        var user = userRepository.findById(presented.getUserId()).orElse(null);
        if (user == null || !user.isActive() || user.isLocked(now)) {
            return Result.failure(INVALID_REFRESH_TOKEN);
        }
        presented.revoke(now);
        refreshTokenRepository.save(presented);
        return Result.success(issueTokens(user, now));
    }

    @Override
    @Transactional
    public void handle(SignOutCommand command) {
        refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(command.refreshToken()))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.revoke(clock.instant());
                    refreshTokenRepository.save(token);
                });
    }

    private AuthTokens issueTokens(User user, Instant now) {
        var refreshToken = refreshTokenGenerator.generate();
        var userId = Objects.requireNonNull(user.getId(), "tokens are issued only for persisted users");
        refreshTokenRepository.save(RefreshToken.issue(
                userId, refreshTokenGenerator.hash(refreshToken), now, refreshTokenTimeToLive));
        return new AuthTokens(tokenService.generateToken(user), tokenService.expirationSeconds(), refreshToken);
    }

    private static ApplicationError accountLocked() {
        return new ApplicationError("ACCOUNT_LOCKED", "The account is temporarily locked",
                "Too many failed attempts; try again in %d minutes".formatted(User.LOCK_DURATION.toMinutes()));
    }
}
