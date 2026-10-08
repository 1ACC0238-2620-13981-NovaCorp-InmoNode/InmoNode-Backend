package com.novacorp.inmonode.inmonodebackend.iam.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.hashing.HashingService;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.RefreshTokenGenerator;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.RefreshToken;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RefreshTokenCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.RegisterUserCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.SignInCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.commands.VerifyEmailCommand;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.AuthTokens;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.RefreshTokenRepository;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class AuthCommandServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final long REFRESH_DAYS = 30;

    private final Map<String, User> users = new HashMap<>();
    private final Map<String, RefreshToken> refreshTokens = new HashMap<>();
    private final AtomicInteger generatedTokens = new AtomicInteger();
    private final UserRepository repository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final HashingService hashing = mock(HashingService.class);
    private final TokenService tokens = mock(TokenService.class);
    private final RefreshTokenGenerator generator = mock(RefreshTokenGenerator.class);
    private AuthCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        when(repository.existsByEmail(any())).thenAnswer(i -> users.containsKey(i.<String>getArgument(0)));
        when(repository.findByEmail(any())).thenAnswer(i -> Optional.ofNullable(users.get(i.<String>getArgument(0))));
        when(repository.findById(any())).thenAnswer(i -> users.values().stream()
                .filter(u -> i.getArgument(0).equals(u.getId())).findFirst());
        when(repository.findByVerificationToken(any())).thenAnswer(i -> users.values().stream()
                .filter(u -> i.getArgument(0).equals(u.getVerificationToken())).findFirst());
        when(repository.save(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            users.put(u.getEmail(), u);
            return u;
        });
        when(refreshTokenRepository.save(any())).thenAnswer(i -> {
            RefreshToken t = i.getArgument(0);
            refreshTokens.put(t.getTokenHash(), t);
            return t;
        });
        when(refreshTokenRepository.findByTokenHash(any()))
                .thenAnswer(i -> Optional.ofNullable(refreshTokens.get(i.<String>getArgument(0))));
        when(refreshTokenRepository.revokeAllActiveByUserId(anyLong(), any())).thenAnswer(i -> {
            var active = refreshTokens.values().stream()
                    .filter(t -> t.getUserId().equals(i.getArgument(0)) && !t.isRevoked())
                    .toList();
            active.forEach(t -> t.revoke(i.getArgument(1)));
            return active.size();
        });
        when(hashing.encode(any())).thenAnswer(i -> "hash:" + i.getArgument(0));
        when(hashing.matches(any(), any())).thenAnswer(i -> ("hash:" + i.getArgument(0)).equals(i.getArgument(1)));
        when(tokens.generateToken(any())).thenReturn("jwt");
        when(tokens.expirationSeconds()).thenReturn(3600L);
        when(generator.generate()).thenAnswer(i -> "rt-" + generatedTokens.incrementAndGet());
        when(generator.hash(any())).thenAnswer(i -> "sha:" + i.getArgument(0));
        service = new AuthCommandServiceImpl(repository, refreshTokenRepository, hashing, tokens, generator,
                REFRESH_DAYS, Clock.fixed(NOW, ZoneOffset.UTC));
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
    void signInReturnsAccessTokenAndStoresOnlyTheHashOfTheRefreshToken() {
        users.put("ana@mail.com", user(UserStatus.ACTIVE));

        var tokens = success(service.handle(new SignInCommand("ana@mail.com", "secret123")));

        assertEquals(new AuthTokens("jwt", 3600, "rt-1"), tokens);
        var stored = refreshTokens.get("sha:rt-1");
        assertEquals(1L, stored.getUserId());
        assertEquals(NOW.plus(Duration.ofDays(REFRESH_DAYS)), stored.getExpiresAt());
        assertFalse(refreshTokens.containsKey("rt-1"));
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
        assertTrue(refreshTokens.isEmpty());
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

    @Test
    void refreshRotatesTheToken() {
        signedInUser();

        var rotated = success(service.handle(new RefreshTokenCommand("rt-1")));

        assertEquals(new AuthTokens("jwt", 3600, "rt-2"), rotated);
        assertEquals(NOW, refreshTokens.get("sha:rt-1").getRevokedAt());
        assertTrue(refreshTokens.get("sha:rt-2").isUsable(NOW));
    }

    @Test
    void replayingARotatedTokenRevokesEveryTokenOfTheUser() {
        signedInUser();
        success(service.handle(new RefreshTokenCommand("rt-1")));

        var replay = service.handle(new RefreshTokenCommand("rt-1"));

        assertEquals("INVALID_REFRESH_TOKEN", failure(replay).code());
        verify(refreshTokenRepository).revokeAllActiveByUserId(1L, NOW);
        assertTrue(refreshTokens.get("sha:rt-2").isRevoked());
        assertEquals("INVALID_REFRESH_TOKEN", failure(service.handle(new RefreshTokenCommand("rt-2"))).code());
    }

    @Test
    void unknownOrExpiredTokensAreRejected() {
        signedInUser();
        refreshTokens.put("sha:old", RefreshToken.issue(1L, "sha:old", NOW.minus(Duration.ofDays(31)),
                Duration.ofDays(REFRESH_DAYS)));

        assertEquals("INVALID_REFRESH_TOKEN", failure(service.handle(new RefreshTokenCommand("nope"))).code());
        assertEquals("INVALID_REFRESH_TOKEN", failure(service.handle(new RefreshTokenCommand("old"))).code());
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(anyLong(), any());
    }

    @Test
    void refreshIsRejectedWhileTheAccountIsLocked() {
        signedInUser();
        var user = users.get("ana@mail.com");
        for (int i = 0; i < User.MAX_FAILED_ATTEMPTS; i++) {
            user.recordFailedAttempt(NOW);
        }

        assertEquals("INVALID_REFRESH_TOKEN", failure(service.handle(new RefreshTokenCommand("rt-1"))).code());
        assertFalse(refreshTokens.get("sha:rt-1").isRevoked());
    }

    /** An active user with id 1 who signed in and holds refresh token "rt-1". */
    private void signedInUser() {
        users.put("ana@mail.com", user(UserStatus.ACTIVE));
        success(service.handle(new SignInCommand("ana@mail.com", "secret123")));
    }

    private static User user(UserStatus status) {
        return User.restore(1L, "ana@mail.com", "hash:secret123", Role.BUYER, status, null, 0, null);
    }

    private static <T> T success(Result<T, ApplicationError> result) {
        return ((Result.Success<T, ApplicationError>) result).value();
    }

    private static <T> ApplicationError failure(Result<T, ApplicationError> result) {
        return ((Result.Failure<T, ApplicationError>) result).error();
    }
}
