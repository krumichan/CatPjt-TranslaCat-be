package jp.co.translacat.domain.novel.platform.repository;

import jp.co.translacat.domain.common.enums.PlatformUrlType;
import jp.co.translacat.domain.novel.platform.entity.PlatformUrlTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformUrlTemplateRepository extends JpaRepository<PlatformUrlTemplate, Long> {
    Optional<PlatformUrlTemplate> findByPlatformIdAndUrlType(Long platformId, PlatformUrlType urlType);
}
