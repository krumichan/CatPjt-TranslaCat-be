package jp.co.translacat.domain.novel.episode.service;

import jp.co.translacat.domain.novel.episode.entity.Episode;
import jp.co.translacat.domain.novel.episode.respository.EpisodeRepository;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EpisodeSafeSaver {
    private final EpisodeRepository episodeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Episode saveEpisode(Novel novel, RawEpisodeContext context) {
        try {
            return this.saveEpisodes(novel, List.of(context)).get().getFirst();
        } catch (Exception e) {
            throw new BusinessException("Failed to save episodes...");
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<List<Episode>> saveEpisodes(Novel novel, List<RawEpisodeContext> contextList) {
        List<Episode> processedList = new ArrayList<>();

        // 에피소드 존재 여부 확인.
        List<Episode> existingEpisodes = episodeRepository
                .findAllByNovelIdAndIdentifierInOrderByIdentifierAsc(
                        novel.getId(), contextList.stream().map(RawEpisodeContext::getIdentifier).toList());
        Map<String, Episode> existingEpisodeMap = existingEpisodes.stream()
                .collect(Collectors.toMap(Episode::getIdentifier, r -> r, (oldValue, newValue) -> oldValue));

        for (RawEpisodeContext context : contextList) {
            Episode maybeEpisode = existingEpisodeMap.get(context.getIdentifier());

            if (!Objects.isNull(maybeEpisode)) {
                // [UPDATE] 이미 있다면 가져와서 정보 갱신
                boolean updated = maybeEpisode.updateIfChanged(novel,
                        context.getTitle().getRawJa(), context.getTitle().getJa(), context.getTitle().getKo());

                if (updated) {
                    processedList.add(maybeEpisode);
                }
            } else {
                // [INSERT] 없다면 새로 생성
                processedList.add(this.episodeRepository.save(
                        Episode.create(novel, context.getIdentifier(),
                                context.getTitle().getRawJa(), context.getTitle().getJa(), context.getTitle().getKo())));
            }
        }

        return CompletableFuture.completedFuture(processedList);
    }
}
