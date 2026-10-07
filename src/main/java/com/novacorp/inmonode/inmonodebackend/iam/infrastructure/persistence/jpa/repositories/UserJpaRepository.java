package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByVerificationToken(String verificationToken);

    boolean existsByEmail(String email);
}
