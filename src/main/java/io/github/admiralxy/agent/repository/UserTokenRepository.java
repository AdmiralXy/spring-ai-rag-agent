package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.UserTokenEntity;
import io.github.admiralxy.agent.security.TokenSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository extends JpaRepository<UserTokenEntity, String> {

    @Query("select new io.github.admiralxy.agent.security.TokenSession(u.id, u.email, t.tokenHash, t.expiresAt) "
            + "from UserTokenEntity t join UserEntity u on u.id = t.userId "
            + "where t.tokenHash = :hash and t.expiresAt > :now")
    Optional<TokenSession> findSession(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("update UserTokenEntity t set t.expiresAt = :expiresAt where t.tokenHash = :hash")
    int extend(@Param("hash") String hash, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Transactional
    @Query("delete from UserTokenEntity t where t.tokenHash = :hash")
    int deleteByHash(@Param("hash") String hash);

    @Modifying
    @Transactional
    @Query("delete from UserTokenEntity t where t.userId = :userId and t.tokenHash <> :keep")
    int deleteOthers(@Param("userId") UUID userId, @Param("keep") String keep);

    @Modifying
    @Transactional
    @Query("delete from UserTokenEntity t where t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
