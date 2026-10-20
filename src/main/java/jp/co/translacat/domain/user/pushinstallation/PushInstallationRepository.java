package jp.co.translacat.domain.user.pushinstallation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PushInstallationRepository extends JpaRepository<PushInstallation, String> {
    Optional<PushInstallation> findByTokenKey(String tokenKey);

    @Query("select count(p) from PushInstallation p, AuthSession s where p.userId = :userId and p.enabled = true "
            + "and p.sessionId = s.id and p.userId = s.userId and s.revokedAt is null and s.expiresAt > :now")
    long countLive(@Param("userId") long userId, @Param("now") Instant now);

    @Query("select p from PushInstallation p, AuthSession s where p.userId in :userIds and p.enabled = true "
            + "and p.appEnvironment = :environment and p.projectId = :projectId "
            + "and p.sessionId = s.id and p.userId = s.userId and s.revokedAt is null and s.expiresAt > :now "
            + "order by p.userId, p.installationId")
    List<PushInstallation> findLive(@Param("userIds") List<Long> userIds, @Param("environment") String environment,
                                    @Param("projectId") String projectId, @Param("now") Instant now);
}
