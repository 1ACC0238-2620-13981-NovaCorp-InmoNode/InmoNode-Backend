package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.RefreshToken;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.RefreshTokenRepository;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.assemblers.RefreshTokenEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.entities.RefreshTokenEntity;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.repositories.RefreshTokenJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Repository
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    public RefreshTokenRepositoryImpl(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        var entity = refreshToken.getId() == null
                ? new RefreshTokenEntity()
                : jpaRepository.findById(refreshToken.getId()).orElseGet(RefreshTokenEntity::new);
        var saved = jpaRepository.save(RefreshTokenEntityAssembler.copyToEntity(refreshToken, entity));
        return RefreshTokenEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(RefreshTokenEntityAssembler::toDomain);
    }

    @Override
    public int revokeAllActiveByUserId(Long userId, Instant now) {
        return jpaRepository.revokeAllActiveByUserId(userId, now, Date.from(now));
    }
}
