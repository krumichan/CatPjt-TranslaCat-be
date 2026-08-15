package jp.co.translacat.domain.languagelearning.speaking.assistance.service;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingAssistanceRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingAssistanceResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.domain.languagelearning.speaking.assistance.dto.request.SpeakingAssistanceRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.assistance.factory.SpeakingAssistanceAiRequestFactory;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionLifecycleService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;
import jp.co.translacat.domain.languagelearning.speaking.usage.service.SpeakingAiUsageCommandService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeakingAssistanceServiceTest {

    @Mock
    private SpeakingSessionQueryService sessionQueryService;
    @Mock
    private SpeakingSessionLifecycleService lifecycleService;
    @Mock
    private SpeakingTurnQueryService turnQueryService;
    @Mock
    private SpeakingAssistanceAiRequestFactory aiRequestFactory;
    @Mock
    private SpeakingAiClient speakingAiClient;
    @Mock
    private SpeakingAiUsageCommandService usageCommandService;
    @Mock
    private SpeakingSession session;
    @Mock
    private SpeakingTurn turn;
    @Mock
    private AiSpeakingAssistanceRequestDto aiRequest;

    private SpeakingAssistanceService service;

    @BeforeEach
    void setUp() {
        service = new SpeakingAssistanceService(
                sessionQueryService,
                lifecycleService,
                turnQueryService,
                aiRequestFactory,
                speakingAiClient,
                usageCommandService
        );

        when(sessionQueryService.getOwnedEntity(7L, 301L))
                .thenReturn(session);
        when(session.getMaxTurns()).thenReturn(20);
        when(session.getCompletedTurns()).thenReturn(5);
        when(turnQueryService.getEntities(301L)).thenReturn(List.of(turn));
        when(turn.getId()).thenReturn(401L);
        when(turn.getAssistantText()).thenReturn("次は何をしたいですか？");
    }

    @Test
    void hintUsesAiAndReturnsGeneratedContent() {
        when(turnQueryService.getOwnedEntity(7L, 301L, 401L))
                .thenReturn(turn);
        when(aiRequestFactory.create(
                session,
                turn,
                List.of(turn),
                AssistanceType.HINT,
                "次は何をしたいですか？"
        )).thenReturn(aiRequest);
        when(speakingAiClient.generateAssistance(aiRequest))
                .thenReturn(new AiSpeakingAssistanceResponseDto(
                        "request-1",
                        "301",
                        6,
                        AssistanceType.HINT,
                        "동사를 떠올려 보세요.",
                        null,
                        false
                ));

        var response = service.get(
                7L,
                301L,
                new SpeakingAssistanceRequestDto(
                        AssistanceType.HINT,
                        401L
                )
        );

        assertThat(response.type()).isEqualTo(AssistanceType.HINT);
        assertThat(response.content()).isEqualTo("동사를 떠올려 보세요.");
        assertThat(response.appliesToTurnIndex()).isEqualTo(6);
        verify(usageCommandService).record(session, 401L, null, 0);
    }

    @Test
    void replayUsesExistingAudioWithoutAiCall() {
        when(turnQueryService.getOwnedEntity(7L, 301L, 401L))
                .thenReturn(turn);
        when(turn.getAssistantAudioObjectKey()).thenReturn("assistant/401.wav");
        when(turn.getSession()).thenReturn(session);
        when(session.getId()).thenReturn(301L);

        var response = service.get(
                7L,
                301L,
                new SpeakingAssistanceRequestDto(
                        AssistanceType.REPLAY,
                        401L
                )
        );

        assertThat(response.audioUrl()).isEqualTo(
                "/api/v1/language-learning/speaking/sessions/301/turns/401/audio"
        );
        assertThat(response.playbackRate()).isEqualTo(1.0);
        verify(speakingAiClient, never()).generateAssistance(any());
    }

    @Test
    void idempotentAiReplayDoesNotRecordUsageAgain() {
        when(turnQueryService.getOwnedEntity(7L, 301L, 401L))
                .thenReturn(turn);
        when(aiRequestFactory.create(
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(aiRequest);
        when(speakingAiClient.generateAssistance(aiRequest))
                .thenReturn(new AiSpeakingAssistanceResponseDto(
                        "request-1",
                        "301",
                        6,
                        AssistanceType.TRANSLATION,
                        "다음에는 무엇을 하고 싶나요?",
                        null,
                        true
                ));

        service.get(
                7L,
                301L,
                new SpeakingAssistanceRequestDto(
                        AssistanceType.TRANSLATION,
                        401L
                )
        );

        verify(usageCommandService, never())
                .record(any(), any(), any(), anyInt());
    }
}
