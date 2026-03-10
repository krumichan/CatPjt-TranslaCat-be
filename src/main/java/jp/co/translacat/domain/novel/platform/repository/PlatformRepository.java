package jp.co.translacat.domain.novel.platform.repository;

import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.common.enums.PlatformCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformRepository extends JpaRepository<Platform, Long> {
    Optional<Platform> findByCode(PlatformCode code);
}
