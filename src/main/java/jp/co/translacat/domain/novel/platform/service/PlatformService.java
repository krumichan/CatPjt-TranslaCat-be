package jp.co.translacat.domain.novel.platform.service;

import jakarta.persistence.EntityNotFoundException;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.PlatformUrlType;
import jp.co.translacat.domain.novel.platform.repository.PlatformRepository;
import jp.co.translacat.domain.novel.platform.repository.PlatformUrlTemplateRepository;
import jp.co.translacat.domain.novel.platform.dto.PlatformResponseDto;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.entity.PlatformUrlTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlatformService {
    private final PlatformRepository platformRepository;
    private final PlatformUrlTemplateRepository platformUrlTemplateRepository;

    public List<PlatformResponseDto> platforms() {
        List<Platform> platforms = platformRepository.findAll();
        return platforms.stream().map(PlatformResponseDto::of).toList();
    }

    public Optional<Platform> findPlatformByCode(PlatformCode code) {
        return platformRepository.findByCode(code);
    }

    public Platform getPlatformByCode(PlatformCode code) {
        return this.findPlatformByCode(code)
            .orElseThrow(() -> new EntityNotFoundException("해당 플랫폼을 찾을 수 없습니다: " + code));
    }

    public Optional<PlatformUrlTemplate> findUrlTemplate(Long platformId, PlatformUrlType urlType) {
        return platformUrlTemplateRepository.findByPlatformIdAndUrlType(platformId, urlType);
    }

    public PlatformUrlTemplate getUrlTemplate(Long platformId, PlatformUrlType urlType) {
        return this.findUrlTemplate(platformId, urlType)
                .orElseThrow(() -> new EntityNotFoundException("해당 타입의 URL을 찾을 수 없습니다: " + platformId + ", " + urlType));
    }
}
