package jp.co.translacat.domain.languagelearning.speaking.assistance.factory;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingConversationMessageDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingAssistanceRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SpeakingAssistanceAiRequestFactory {

    private final LanguageLearningJsonCodec jsonCodec;

    public AiSpeakingAssistanceRequestDto create(
            SpeakingSession session,
            SpeakingTurn targetTurn,
            List<SpeakingTurn> history,
            AssistanceType type,
            String assistantText
    ) {
        int nextTurnIndex = Math.min(
                session.getMaxTurns(),
                Math.max(1, session.getCompletedTurns() + 1)
        );
        String targetKey = targetTurn == null
                ? "opening"
                : String.valueOf(targetTurn.getId());

        return new AiSpeakingAssistanceRequestDto(
                "speaking-assistance-" + session.getId()
                        + "-" + targetKey + "-" + type.name(),
                "speaking-assistance:" + session.getId()
                        + ":" + targetKey + ":" + type.name(),
                String.valueOf(session.getId()),
                nextTurnIndex,
                type,
                session.getOriginLanguage(),
                session.getLearningLanguage(),
                session.getTopicTitle(),
                session.getTopic() == null
                        ? null
                        : session.getTopic().getRecommendedLevel(),
                assistantText,
                conversationHistory(history),
                selectedKeywords(session),
                session.getSessionSummary()
        );
    }

    private List<AiSpeakingConversationMessageDto> conversationHistory(
            List<SpeakingTurn> turns
    ) {
        List<AiSpeakingConversationMessageDto> result = new ArrayList<>();
        for (SpeakingTurn turn : turns) {
            if (turn.getTranscript() != null && !turn.getTranscript().isBlank()) {
                result.add(new AiSpeakingConversationMessageDto(
                        "USER",
                        turn.getTranscript(),
                        String.valueOf(turn.getId())
                ));
            }
            if (turn.getAssistantText() != null
                    && !turn.getAssistantText().isBlank()) {
                result.add(new AiSpeakingConversationMessageDto(
                        "ASSISTANT",
                        turn.getAssistantText(),
                        String.valueOf(turn.getId())
                ));
            }
        }
        return List.copyOf(result);
    }

    private List<SelectedKeywordDto> selectedKeywords(SpeakingSession session) {
        if (session.getSelectedKeywordsJson() == null
                || session.getSelectedKeywordsJson().isBlank()) {
            return List.of();
        }
        return jsonCodec.read(
                session.getSelectedKeywordsJson(),
                new TypeReference<List<SelectedKeywordDto>>() {
                }
        );
    }
}
