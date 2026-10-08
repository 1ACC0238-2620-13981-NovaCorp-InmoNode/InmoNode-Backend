package com.novacorp.inmonode.inmonodebackend.iam.application.acl;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.acl.IamContextFacade;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads the caller from the security context filled by the JWT filter (US-31, Scenario 2)
 * and resolves other users through the repository.
 */
@Service
public class IamContextFacadeImpl implements IamContextFacade {

    private final UserRepository userRepository;

    public IamContextFacadeImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<Long> currentUserId() {
        return currentUser().map(AuthenticatedUser::userId);
    }

    @Override
    public Optional<String> currentUserRole() {
        return currentUser().map(user -> user.role().name());
    }

    @Override
    public Optional<String> fetchEmailByUserId(Long userId) {
        return userRepository.findById(userId).map(User::getEmail);
    }

    private static Optional<AuthenticatedUser> currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
