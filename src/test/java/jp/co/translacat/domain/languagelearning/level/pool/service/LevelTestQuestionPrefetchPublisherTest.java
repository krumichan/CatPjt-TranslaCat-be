package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestAdaptivePolicy;
import jp.co.translacat.domain.languagelearning.level.pool.event.LevelTestQuestionPrefetchRequestedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevelTestQuestionPrefetchPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private LevelTestItem item;

    @Mock
    private LevelTestSession session;

    private LevelTestQuestionPrefetchPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new LevelTestQuestionPrefetchPublisher(
                eventPublisher,
                new LevelTestAdaptivePolicy()
        );
    }

    @Test
    void publishesAllReachableAdaptiveBandsForNextQuestion() {
        when(item.getQuestionNumber()).thenReturn(1);
        when(item.getAnswerMode()).thenReturn(LevelTestAnswerMode.CHOICE);
        when(item.getComplexityBandValue()).thenReturn(2);
        when(item.getSession()).thenReturn(session);
        when(session.getId()).thenReturn(77L);

        publisher.publish(item);

        ArgumentCaptor<LevelTestQuestionPrefetchRequestedEvent> captor =
                ArgumentCaptor.forClass(
                        LevelTestQuestionPrefetchRequestedEvent.class
                );
        verify(eventPublisher, times(2)).publishEvent(captor.capture());

        List<LevelTestQuestionPrefetchRequestedEvent> events =
                captor.getAllValues();
        assertThat(events)
                .extracting(
                        LevelTestQuestionPrefetchRequestedEvent::questionNumber,
                        LevelTestQuestionPrefetchRequestedEvent::complexityBand
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2, 1),
                        org.assertj.core.groups.Tuple.tuple(2, 3)
                );
    }

    @Test
    void doesNotPrefetchAfterLastQuestion() {
        when(item.getQuestionNumber()).thenReturn(20);

        publisher.publish(item);

        verify(eventPublisher, never()).publishEvent(
                org.mockito.ArgumentMatchers.any()
        );
    }
}
