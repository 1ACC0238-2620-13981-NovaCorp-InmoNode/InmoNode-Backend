package com.novacorp.inmonode.inmonodebackend.iam.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;

import java.util.Optional;

/**
 * Persistence abstraction for the {@link User} aggregate.
 */
public interface UserRepository {

    /**
     * Persists the aggregate and publishes the domain events it registered.
     *
     * @return the persisted user, with its generated id
     */
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByVerificationToken(String token);

    boolean existsByEmail(String email);
}
