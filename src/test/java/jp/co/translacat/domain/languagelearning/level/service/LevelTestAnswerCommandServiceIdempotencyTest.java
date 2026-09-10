package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevelTestAnswerCommandServiceIdempotencyTest {

    @Mock
    private LevelTestItemRepository itemRepository;
    @Mock
    private LevelTestResponseRepository responseRepository;
    @Mock
    private LevelTestSessionRepository sessionRepository;
    @Mock
    private LanguageLearningJsonCodec jsonCodec;
    @Mock
    private LevelTestItem item;
    @Mock
    private LevelTestSession session;
    @Mock
    private LevelTestResponse response;
    @Mock
    private User user;

    private LevelTestAnswerCommandService service;

    @BeforeEach
    void setUp() {
        service = new LevelTestAnswerCommandService(
                itemRepository,
                responseRepository,
                sessionRepository,
                jsonCodec
        );
        when(itemRepository.findLockedById(10L))
                .thenReturn(Optional.of(item));
        when(item.getSession()).thenReturn(session);
        when(session.getId()).thenReturn(1L);
        when(session.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
    }

    @Test
    void textReplayWithSameKeyBypassesAlreadyAdvancedItemState() {
        when(item.getAnswerMode()).thenReturn(LevelTestAnswerMode.CHOICE);
        when(responseRepository.findByItemIdAndIdempotencyKey(10L, "same-key"))
                .thenReturn(Optional.of(response));

        LevelTestAnswerCommandService.PreparedResponse result =
                service.prepareText(
                        7L,
                        1L,
                        10L,
                        new LevelAnswerRequestDto(
                                "A",
                                List.of(),
                                null,
                                "same-key"
                        )
                );

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.response()).isSameAs(response);
        verify(responseRepository, never()).findByItemId(10L);
        verify(sessionRepository, never()).findLockedById(1L);
    }

    @Test
    void audioReplayWithSameKeyBypassesAlreadyAdvancedItemState() {
        when(item.getAnswerMode()).thenReturn(LevelTestAnswerMode.AUDIO);
        when(item.getMaxAudioSeconds()).thenReturn(30);
        when(responseRepository.findByItemIdAndIdempotencyKey(10L, "same-key"))
                .thenReturn(Optional.of(response));

        LevelTestAnswerCommandService.PreparedResponse result =
                service.prepareAudioMetadata(
                        7L,
                        1L,
                        10L,
                        "level/10.webm",
                        "audio/webm",
                        5_000,
                        "same-key",
                        LocalDateTime.now().plusDays(7)
                );

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.response()).isSameAs(response);
        verify(responseRepository, never()).findByItemId(10L);
        verify(sessionRepository, never()).findLockedById(1L);
    }

    @Test
    void failedAudioEvaluationCanBeReplacedByANewRecording() {
        LocalDateTime retentionUntil = LocalDateTime.now().plusDays(7);
        when(item.getAnswerMode()).thenReturn(LevelTestAnswerMode.AUDIO);
        when(item.getMaxAudioSeconds()).thenReturn(30);
        when(item.getStatus()).thenReturn(LevelTestItemStatus.EVALUATION_FAILED);
        when(item.getQuestionNumber()).thenReturn(18);
        when(session.currentQuestionNumber()).thenReturn(18);
        when(session.getStatus()).thenReturn(LevelTestSessionStatus.IN_PROGRESS);
        when(responseRepository.findByItemIdAndIdempotencyKey(10L, "new-audio-key"))
                .thenReturn(Optional.empty());
        when(responseRepository.findByItemId(10L)).thenReturn(Optional.of(response));
        when(responseRepository.save(response)).thenReturn(response);
        when(sessionRepository.findLockedById(1L)).thenReturn(Optional.of(session));

        LevelTestAnswerCommandService.PreparedResponse result =
                service.prepareAudioMetadata(
                        7L,
                        1L,
                        10L,
                        "level/10-rerecord.webm",
                        "audio/webm;codecs=opus",
                        2_900,
                        "new-audio-key",
                        retentionUntil
                );

        assertThat(result.idempotentReplay()).isFalse();
        assertThat(result.response()).isSameAs(response);
        verify(response).replaceAudio(
                eq("level/10-rerecord.webm"),
                eq("audio/webm;codecs=opus"),
                eq(2_900),
                eq(retentionUntil),
                eq("new-audio-key"),
                any(LocalDateTime.class)
        );
        verify(item).markAnswered();
        verify(item).markEvaluating();
        verify(session).markEvaluating(any(LocalDateTime.class));
    }

    @Test
    void differentKeyCannotReplaceExistingResponse() {
        when(item.getAnswerMode()).thenReturn(LevelTestAnswerMode.CHOICE);
        when(responseRepository.findByItemIdAndIdempotencyKey(10L, "new-key"))
                .thenReturn(Optional.empty());
        when(responseRepository.findByItemId(10L))
                .thenReturn(Optional.of(response));

        assertThatThrownBy(() -> service.prepareText(
                7L,
                1L,
                10L,
                new LevelAnswerRequestDto(
                        "A",
                        List.of(),
                        null,
                        "new-key"
                )
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 제출된 Level Test 답변");

        verify(sessionRepository, never()).findLockedById(1L);
    }
}
