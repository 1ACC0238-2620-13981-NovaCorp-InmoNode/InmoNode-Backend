package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications.PasswordResetEmailSender;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications.VerificationEmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full registration, verification and sign-in flow over HTTP against a real PostgreSQL.
 * Every test registers its own email, so they do not depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthControllerIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final String NEW_PASSWORD = "new-secret456";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerificationEmailSender verificationEmailSender;

    @MockitoBean
    private PasswordResetEmailSender passwordResetEmailSender;

    @Test
    void registerCreatesInactiveAccountAndRejectsDuplicate() throws Exception {
        var email = uniqueEmail();

        register(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        register(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_CONFLICT"));
    }

    @Test
    void loginBeforeVerificationIsForbidden() throws Exception {
        var email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void errorMessageFollowsAcceptLanguageWithoutMixingLanguages() throws Exception {
        var email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        var body = """
                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                .andExpect(jsonPath("$.message").value("Cuenta no activa: verifica tu correo antes de iniciar sesión"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.message").value("Account not active: verify your email before signing in"));
    }

    @Test
    void verifiedAccountCanSignIn() throws Exception {
        var email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        var token = capturedVerificationToken(email);

        verifyEmail(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refreshRotatesTheTokenAndAReplayClosesEverySession() throws Exception {
        var email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        verifyEmail(capturedVerificationToken(email)).andExpect(status().isOk());
        var first = refreshTokenOf(login(email, PASSWORD).andExpect(status().isOk()));

        var second = refreshTokenOf(refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty()));
        assertNotEquals(first, second);

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutEndsTheSessionAndAlwaysAnswersNoContent() throws Exception {
        var email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        verifyEmail(capturedVerificationToken(email)).andExpect(status().isOk());
        var refreshToken = refreshTokenOf(login(email, PASSWORD).andExpect(status().isOk()));

        logout(refreshToken).andExpect(status().isNoContent());

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        logout(refreshToken).andExpect(status().isNoContent());
        logout("not-a-refresh-token").andExpect(status().isNoContent());
        logout("").andExpect(status().isBadRequest());
    }

    @Test
    void refreshWithUnknownOrBlankTokenIsRejected() throws Exception {
        refresh("not-a-refresh-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void verificationTokenIsSingleUseAndUnknownTokensAreRejected() throws Exception {
        var email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        var token = capturedVerificationToken(email);
        verifyEmail(token).andExpect(status().isOk());

        verifyEmail(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyEmail(UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void resendVerificationEmailsANewLinkAndTheOldOneStopsWorking() throws Exception {
        var email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        resendVerification(email).andExpect(status().isNoContent());

        var tokens = ArgumentCaptor.forClass(String.class);
        verify(verificationEmailSender, times(2)).send(eq(email), tokens.capture());
        var first = tokens.getAllValues().get(0);
        var second = tokens.getAllValues().get(1);
        assertNotEquals(first, second);
        verifyEmail(first)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyEmail(second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void resendVerificationRevealsNothingAndSendsOnlyForPendingAccounts() throws Exception {
        var active = uniqueEmail();
        register(active).andExpect(status().isCreated());
        verifyEmail(capturedVerificationToken(active)).andExpect(status().isOk());
        var unknown = uniqueEmail();

        resendVerification(active).andExpect(status().isNoContent());
        resendVerification(unknown).andExpect(status().isNoContent());
        resendVerification("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(verificationEmailSender, times(1)).send(eq(active), anyString());
        verify(verificationEmailSender, never()).send(eq(unknown), anyString());
    }

    @Test
    void fifthWrongPasswordLocksTheAccount() throws Exception {
        var email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        verifyEmail(capturedVerificationToken(email)).andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login(email, "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        login(email, "wrong-password")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        login(email, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void forgottenPasswordIsResetOnceThroughTheEmailedLinkAndEverySessionEnds() throws Exception {
        var email = activeAccount();
        var oldSession = refreshTokenOf(login(email, PASSWORD).andExpect(status().isOk()));

        forgotPassword(email.toUpperCase()).andExpect(status().isNoContent());
        var token = capturedResetToken(email);
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        refresh(oldSession)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        login(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        resetPassword(token, "another-secret789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void resettingThePasswordLiftsTheLockFromFailedAttempts() throws Exception {
        var email = activeAccount();
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password");
        }
        login(email, PASSWORD).andExpect(status().isLocked());

        forgotPassword(email).andExpect(status().isNoContent());
        resetPassword(capturedResetToken(email), NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void forgotPasswordRevealsNothingAndSendsOnlyToActiveAccounts() throws Exception {
        var pending = uniqueEmail();
        register(pending).andExpect(status().isCreated());
        var unknown = uniqueEmail();

        forgotPassword(pending).andExpect(status().isNoContent());
        forgotPassword(unknown).andExpect(status().isNoContent());
        forgotPassword("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(passwordResetEmailSender, never()).send(anyString(), anyString());
    }

    @Test
    void resetPasswordRejectsUnknownTokensAndInvalidPasswords() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "es")
                        .content("""
                                {"token": "not-a-reset-token", "newPassword": "%s"}""".formatted(NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"))
                .andExpect(jsonPath("$.message").value(
                        "El enlace para restablecer la contraseña no es válido, venció o ya se usó; pide uno nuevo"));
        resetPassword("", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        var email = activeAccount();
        forgotPassword(email).andExpect(status().isNoContent());
        var token = capturedResetToken(email);
        resetPassword(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    /** Registers and verifies a new account with {@link #PASSWORD}, and returns its email. */
    private String activeAccount() throws Exception {
        var email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        verifyEmail(capturedVerificationToken(email)).andExpect(status().isOk());
        return email;
    }

    private ResultActions register(String email) throws Exception {
        return postJson("/api/v1/auth/register", """
                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", """
                {"email": "%s", "password": "%s"}""".formatted(email, password));
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return postJson("/api/v1/auth/verify-email", """
                {"token": "%s"}""".formatted(token));
    }

    private ResultActions resendVerification(String email) throws Exception {
        return postJson("/api/v1/auth/resend-verification", """
                {"email": "%s"}""".formatted(email));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/refresh", """
                {"refreshToken": "%s"}""".formatted(refreshToken));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/logout", """
                {"refreshToken": "%s"}""".formatted(refreshToken));
    }

    private ResultActions forgotPassword(String email) throws Exception {
        return postJson("/api/v1/auth/forgot-password", """
                {"email": "%s"}""".formatted(email));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return postJson("/api/v1/auth/reset-password", """
                {"token": "%s", "newPassword": "%s"}""".formatted(token, newPassword));
    }

    private static String refreshTokenOf(ResultActions response) throws Exception {
        return JsonPath.read(response.andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String capturedVerificationToken(String email) {
        var token = ArgumentCaptor.forClass(String.class);
        verify(verificationEmailSender).send(eq(email), token.capture());
        return token.getValue();
    }

    private String capturedResetToken(String email) {
        var token = ArgumentCaptor.forClass(String.class);
        verify(passwordResetEmailSender).send(eq(email), token.capture());
        return token.getValue();
    }

    private static String uniqueEmail() {
        return "buyer-" + UUID.randomUUID() + "@mail.com";
    }
}
