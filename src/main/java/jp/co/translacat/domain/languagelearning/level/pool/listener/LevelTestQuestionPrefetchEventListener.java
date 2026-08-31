package jp.co.translacat.domain.languagelearning.level.pool.listener;

import jp.co.translacat.domain.languagelearning.level.pool.event.LevelTestQuestionPrefetchRequestedEvent;
import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionPrefetchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LevelTestQuestionPrefetchEventListener {

    private final LevelTestQuestionPrefetchService prefetchService;

    @Async("levelTestPrefetchExecutor")
    @EventListener
    public void handle(LevelTestQuestionPrefetchRequestedEvent event) {
        try {
            prefetchService.prefetch(
                    event.sessionId(),
                    event.questionNumber(),
                    event.complexityBand()
            );
        } catch (Exception exception) {
            log.warn(
                    "Level Test question prefetch failed. sessionId={}, questionNumber={}, band={}",
                    event.sessionId(),
                    event.questionNumber(),
                    event.complexityBand(),
                    exception
            );
        }
    }
}
