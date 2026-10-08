package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.entities.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /** Bulk update: JPA auditing does not run here, so {@code updatedAt} is set explicitly. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshTokenEntity t
               set t.revokedAt = :now, t.updatedAt = :updatedAt
             where t.userId = :userId and t.revokedAt is null""")
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now,
                                @Param("updatedAt") Date updatedAt);
}
