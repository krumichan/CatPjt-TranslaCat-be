package jp.co.translacat.domain.novelgateway;

import jp.co.translacat.domain.novel.episode.entity.Episode;
import jp.co.translacat.domain.novel.episode.entity.EpisodeContent;
import jp.co.translacat.domain.novel.episode.respository.EpisodeContentRepository;
import jp.co.translacat.domain.novel.episode.respository.EpisodeRepository;
import jp.co.translacat.domain.novel.episode.service.EpisodeContentSafeSaver;
import jp.co.translacat.domain.novel.episode.service.EpisodeSafeSaver;
import jp.co.translacat.domain.novel.episode.service.EpisodeContentSnapshot;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyNovelPersistenceTest {
    @Test
    void unchangedCachedEpisodeStillReturnsExistingEntity() {
        // 준비: 번역 캐시 적중 시 metadata에 변경이 없더라도 회차는 존재한다.
        var novel = mock(Novel.class);
        when(novel.getId()).thenReturn(7L);
        var episode = Episode.create(novel, "1", "雨", "雨", "비");
        var repository = mock(EpisodeRepository.class);
        when(repository.findAllByNovelIdAndIdentifierInOrderByIdentifierAsc(7L, List.of("1")))
                .thenReturn(List.of(episode));
        var context = RawEpisodeContext.builder().identifier("1").title(TranslationUnit.of("雨", "雨", "비")).build();

        // 실행 및 검증
        assertThat(new EpisodeSafeSaver(repository).saveEpisode(novel, context)).isSameAs(episode);
        verify(repository, never()).save(any());
    }

    @Test
    void invalidTranslationCannotEraseExistingContents() {
        // 준비
        var repository = mock(EpisodeContentRepository.class);
        var episode = mock(Episode.class);
        when(episode.getId()).thenReturn(3L);
        var saver = new EpisodeContentSafeSaver(repository, mock(EpisodeRepository.class), mock(FuriganaProcessor.class));
        var invalid = EpisodeContent.create(episode, 0, "雨。", "雨。", "");

        // 실행 및 검증
        assertThatThrownBy(() -> saver.saveEpisodeContents(episode, List.of(invalid), EpisodeContentSnapshot.fingerprint(List.of())))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).deleteAllByEpisodeId(anyLong());
        verify(repository, never()).batchInsertAll(anyList());
    }

    @Test
    void newerPersistedContentsAreNeverOverwrittenByLateResult() {
        // 준비: 번역을 시작할 때 비어 있었지만 다른 작업이 먼저 저장했다.
        var repository = mock(EpisodeContentRepository.class);
        var episodeRepository = mock(EpisodeRepository.class);
        var episode = mock(Episode.class);
        when(episode.getId()).thenReturn(3L);
        when(episodeRepository.findForContentUpdate(3L)).thenReturn(java.util.Optional.of(episode));
        when(repository.findAllByEpisodeIdOrderBySequenceAsc(3L))
                .thenReturn(List.of(EpisodeContent.create(episode, 0, "新しい雨。", "新しい雨。", "새로운 비.")));
        var saver = new EpisodeContentSafeSaver(repository, episodeRepository, mock(FuriganaProcessor.class));
        var late = EpisodeContent.create(episode, 0, "雨。", "雨。", "비.");

        // 실행 및 검증
        assertThatThrownBy(() -> saver.saveEpisodeContents(episode, List.of(late), EpisodeContentSnapshot.fingerprint(List.of())))
                .hasMessageContaining("existing data preserved");
        verify(repository, never()).deleteAllByEpisodeId(anyLong());
        verify(repository, never()).batchInsertAll(anyList());
    }

    @Test
    void rolledBackParentDoesNotScheduleContentReplacement() {
        // 준비
        var callback = mock(Runnable.class);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            jp.co.translacat.global.utils.TransactionUtil.runAfterCommit(callback);

            // 실행: 상위 트랜잭션이 실패했다.
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));

            // 검증
            verifyNoInteractions(callback);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }
}
