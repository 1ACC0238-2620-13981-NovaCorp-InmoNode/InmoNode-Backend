package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.assemblers.UserEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.entities.UserEntity;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.repositories.UserJpaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserRepositoryImpl(UserJpaRepository jpaRepository, ApplicationEventPublisher eventPublisher) {
        this.jpaRepository = jpaRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public User save(User user) {
        var entity = user.getId() == null
                ? new UserEntity()
                : jpaRepository.findById(user.getId()).orElseGet(UserEntity::new);
        var saved = jpaRepository.save(UserEntityAssembler.copyToEntity(user, entity));
        user.domainEvents().forEach(eventPublisher::publishEvent);
        user.clearDomainEvents();
        return UserEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id).map(UserEntityAssembler::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(UserEntityAssembler::toDomain);
    }

    @Override
    public Optional<User> findByVerificationToken(String token) {
        return jpaRepository.findByVerificationToken(token).map(UserEntityAssembler::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }
}
