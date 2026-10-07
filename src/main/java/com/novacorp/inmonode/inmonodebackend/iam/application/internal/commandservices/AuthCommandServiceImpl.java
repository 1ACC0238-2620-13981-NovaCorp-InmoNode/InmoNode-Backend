package com.novacorp.inmonode.inmonodebackend.iam.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.hashing.HashingService;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RegisterUserCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignInCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.VerifyEmailCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
import com.novacorp.inmonode.inmonodebackend.iam.domain.services.AuthCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuthCommandServiceImpl implements AuthCommandService {

    static final ApplicationError INVALID_CREDENTIALS =
            new ApplicationError("INVALID_CREDENTIALS", "Invalid email or password");

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;
    private final Clock clock;

    public AuthCommandServiceImpl(UserRepository userRepository, HashingService hashingService,
                                  TokenService tokenService, Clock clock) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
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
    public Result<String, ApplicationError> handle(SignInCommand command) {
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
        return Result.success(tokenService.generateToken(user));
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

    private static ApplicationError accountLocked() {
        return new ApplicationError("ACCOUNT_LOCKED", "The account is temporarily locked",
                "Too many failed attempts; try again in %d minutes".formatted(User.LOCK_DURATION.toMinutes()));
    }
}
