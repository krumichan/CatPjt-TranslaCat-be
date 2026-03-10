package jp.co.translacat.domain.user.service;

import jakarta.persistence.EntityNotFoundException;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.domain.user.dto.RecentViewSaveRequestDto;
import jp.co.translacat.domain.user.repository.RecentViewRepository;
import jp.co.translacat.domain.user.entity.RecentView;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.RecentViewType;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentViewService {
    private final RecentViewRepository recentViewRepository;

    private final UserService userService;
    private final PlatformService platformService;

    public Optional<RecentView> findBy(Long userId, Long platformId, RecentViewType recentViewType, String novelId) {
        return this.findBy(userId, platformId, recentViewType, novelId, null);
    }

    public Optional<RecentView> findBy(Long userId, Long platformId, RecentViewType recentViewType, String novelId, String episodeId) {
        if (Objects.isNull(episodeId)) {
            return this.recentViewRepository.findByUserIdAndPlatformIdAndRecentViewTypeAndNovelIdAndEpisodeIdIsNull(
                userId, platformId, recentViewType, novelId
            );
        }
        return this.recentViewRepository.findByUserIdAndPlatformIdAndRecentViewTypeAndNovelIdAndEpisodeId(
            userId, platformId, recentViewType, novelId, episodeId
        );
    }

    @Transactional
    public Boolean delete(Long recentViewId) {
        User user = userService.findByEmail(SecurityUtil.getUsername());

        RecentView recentView = recentViewRepository.findById(recentViewId)
                .orElseThrow(() -> new EntityNotFoundException("Recent View is not exist: " + recentViewId));

        if (!recentView.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Is not your recent view...");
        }

        this.recentViewRepository.deleteById(recentViewId);

        return true;
    }

    public List<RecentView> findTop5By(RecentViewType recentViewType) {
        User user = userService.findByEmail(SecurityUtil.getUsername());
        return this.recentViewRepository.findTop5ByUserIdAndRecentViewTypeOrderByViewedAtDesc(
            user.getId(), recentViewType);
    }

    @Transactional
    public boolean save(RecentViewSaveRequestDto requestDto) {
        this.save(requestDto.getPlatformCode(), requestDto.getType(),
                requestDto.getNovelId(), requestDto.getEpisodeId(),
                requestDto.getTitle(), requestDto.getTitleJa(), requestDto.getTitleKo());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(PlatformCode platformCode, RecentViewType recentViewType,
                     String novelId, String episodeId, String title, String titleJa, String titleKo) {
        User user = userService.findByEmail(SecurityUtil.getUsername());
        Platform platform = platformService.getPlatformByCode(platformCode);

        this.findBy(user.getId(), platform.getId(), recentViewType, novelId, episodeId)
            .ifPresentOrElse(
                recentView -> recentView.update(title, titleJa, titleKo),
                () -> recentViewRepository.save(RecentView.create(
                    user, platform, recentViewType, novelId, episodeId, title, titleJa, titleKo
                ))
            );
    }
}
