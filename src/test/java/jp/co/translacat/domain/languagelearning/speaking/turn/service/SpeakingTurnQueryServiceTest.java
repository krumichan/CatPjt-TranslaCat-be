package jp.co.translacat.domain.languagelearning.speaking.turn.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeakingTurnQueryServiceTest {

    @Mock
    private SpeakingTurnRepository turnRepository;
    @Mock
    private LanguageLearningJsonCodec jsonCodec;
    @Mock
    private SpeakingTurn turn;
    @Mock
    private SpeakingSession session;

    @Test
    void responseExposesRetainedUserAudioAndAssistanceUsage() {
        SpeakingTurnQueryService service = new SpeakingTurnQueryService(
                turnRepository,
                jsonCodec
        );
        when(turn.getId()).thenReturn(401L);
        when(turn.getTurnIndex()).thenReturn(1);
        when(turn.getUserAudioObjectKey()).thenReturn("user/401.webm");
        when(turn.getAssistantAudioObjectKey()).thenReturn("assistant/401.wav");
        when(turn.getSession()).thenReturn(session);
        when(session.getId()).thenReturn(301L);
        when(turn.getAssistanceUsageJson())
                .thenReturn("[\"HINT\",\"SAMPLE_ANSWER\"]");
        when(jsonCodec.read(
                eq("[\"HINT\",\"SAMPLE_ANSWER\"]"),
                any(TypeReference.class)
        )).thenReturn(List.of(
                AssistanceType.HINT,
                AssistanceType.SAMPLE_ANSWER
        ));

        var response = service.toResponse(turn);

        assertThat(response.userAudioUrl()).isEqualTo(
                "/api/v1/language-learning/speaking/sessions/301/turns/401/audio/user"
        );
        assertThat(response.assistantAudioUrl()).isEqualTo(
                "/api/v1/language-learning/speaking/sessions/301/turns/401/audio"
        );
        assertThat(response.assistanceUsage()).containsExactly(
                AssistanceType.HINT,
                AssistanceType.SAMPLE_ANSWER
        );
    }
}
