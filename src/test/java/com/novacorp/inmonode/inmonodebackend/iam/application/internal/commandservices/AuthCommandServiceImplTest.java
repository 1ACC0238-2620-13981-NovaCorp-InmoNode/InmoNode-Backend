package com.novacorp.inmonode.inmonodebackend.iam.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.hashing.HashingService;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RegisterUserCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignInCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.VerifyEmailCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthCommandServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    private final Map<String, User> users = new HashMap<>();
    private final UserRepository repository = mock(UserRepository.class);
    private final HashingService hashing = mock(HashingService.class);
    private final TokenService tokens = mock(TokenService.class);
    private AuthCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        when(repository.existsByEmail(any())).thenAnswer(i -> users.containsKey(i.<String>getArgument(0)));
        when(repository.findByEmail(any())).thenAnswer(i -> Optional.ofNullable(users.get(i.<String>getArgument(0))));
        when(repository.findByVerificationToken(any())).thenAnswer(i -> users.values().stream()
                .filter(u -> i.getArgument(0).equals(u.getVerificationToken())).findFirst());
        when(repository.save(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            users.put(u.getEmail(), u);
            return u;
        });
        when(hashing.encode(any())).thenAnswer(i -> "hash:" + i.getArgument(0));
        when(hashing.matches(any(), any())).thenAnswer(i -> ("hash:" + i.getArgument(0)).equals(i.getArgument(1)));
        when(tokens.generateToken(any())).thenReturn("jwt");
        service = new AuthCommandServiceImpl(repository, hashing, tokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registerCreatesInactiveBuyer() {
        var result = service.handle(new RegisterUserCommand("Ana@Mail.com", "secret123"));

        var user = ((Result.Success<User, ApplicationError>) result).value();
        assertEquals("ana@mail.com", user.getEmail());
        assertEquals(Role.BUYER, user.getRole());
        assertEquals(UserStatus.INACTIVE, user.getStatus());
        assertEquals("hash:secret123", user.getPasswordHash());
    }

    @Test
    void registerRejectsExistingEmail() {
        service.handle(new RegisterUserCommand("ana@mail.com", "secret123"));

        var result = service.handle(new RegisterUserCommand("ANA@mail.com", "other-pass"));

        assertEquals("USER_CONFLICT", failure(result).code());
    }

    @Test
    void signInReturnsTokenForActiveUser() {
        users.put("ana@mail.com", user(UserStatus.ACTIVE));

        var result = service.handle(new SignInCommand("ana@mail.com", "secret123"));

        assertEquals("jwt", ((Result.Success<String, ApplicationError>) result).value());
    }

    @Test
    void signInWithWrongPasswordOrUnknownEmailGivesSameError() {
        users.put("ana@mail.com", user(UserStatus.ACTIVE));

        assertEquals("INVALID_CREDENTIALS", failure(service.handle(new SignInCommand("ana@mail.com", "bad"))).code());
        assertEquals("INVALID_CREDENTIALS", failure(service.handle(new SignInCommand("nobody@mail.com", "bad"))).code());
    }

    @Test
    void fifthFailedAttemptLocksAndBlocksEvenCorrectPassword() {
        users.put("ana@mail.com", user(UserStatus.ACTIVE));

        for (int i = 0; i < 4; i++) {
            assertEquals("INVALID_CREDENTIALS", failure(service.handle(new SignInCommand("ana@mail.com", "bad"))).code());
        }
        assertEquals("ACCOUNT_LOCKED", failure(service.handle(new SignInCommand("ana@mail.com", "bad"))).code());
        assertEquals("ACCOUNT_LOCKED", failure(service.handle(new SignInCommand("ana@mail.com", "secret123"))).code());
        verify(tokens, never()).generateToken(any());
    }

    @Test
    void signInRejectsInactiveAccountOnlyAfterPasswordIsCorrect() {
        users.put("ana@mail.com", user(UserStatus.INACTIVE));

        assertEquals("INVALID_CREDENTIALS", failure(service.handle(new SignInCommand("ana@mail.com", "bad"))).code());
        assertEquals("ACCOUNT_INACTIVE", failure(service.handle(new SignInCommand("ana@mail.com", "secret123"))).code());
    }

    @Test
    void verifyEmailActivatesAccountAndRejectsReuse() {
        var registered = ((Result.Success<User, ApplicationError>) service.handle(
                new RegisterUserCommand("ana@mail.com", "secret123"))).value();
        var token = registered.getVerificationToken();

        var first = service.handle(new VerifyEmailCommand(token));
        var second = service.handle(new VerifyEmailCommand(token));

        assertEquals(UserStatus.ACTIVE, ((Result.Success<User, ApplicationError>) first).value().getStatus());
        assertEquals("VALIDATION_ERROR", failure(second).code());
    }

    private static User user(UserStatus status) {
        return User.restore(1L, "ana@mail.com", "hash:secret123", Role.BUYER, status, null, 0, null);
    }

    private static <T> ApplicationError failure(Result<T, ApplicationError> result) {
        return ((Result.Failure<T, ApplicationError>) result).error();
    }
}
