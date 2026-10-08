package com.novacorp.inmonode.inmonodebackend.iam.application.acl;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.model.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IamContextFacadeImplTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final IamContextFacadeImpl facade = new IamContextFacadeImpl(repository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesTheAuthenticatedCaller() {
        var caller = new AuthenticatedUser(7L, "agent@mail.com", Role.FIELD_AGENT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                caller, null, AuthorityUtils.createAuthorityList("ROLE_FIELD_AGENT")));

        assertEquals(Optional.of(7L), facade.currentUserId());
        assertEquals(Optional.of("FIELD_AGENT"), facade.currentUserRole());
    }

    @Test
    void isEmptyWithoutAuthentication() {
        assertTrue(facade.currentUserId().isEmpty());
        assertTrue(facade.currentUserRole().isEmpty());
    }

    @Test
    void ignoresPrincipalsThatAreNotIamUsers() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertTrue(facade.currentUserId().isEmpty());
        assertTrue(facade.currentUserRole().isEmpty());
    }

    @Test
    void fetchesEmailOfExistingUserOnly() {
        var user = User.restore(7L, "agent@mail.com", "hash", Role.FIELD_AGENT, UserStatus.ACTIVE, null, null, 0, null);
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(Optional.of("agent@mail.com"), facade.fetchEmailByUserId(7L));
        assertTrue(facade.fetchEmailByUserId(99L).isEmpty());
    }
}
