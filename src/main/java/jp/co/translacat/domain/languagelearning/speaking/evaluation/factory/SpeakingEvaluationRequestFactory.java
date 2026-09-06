package jp.co.translacat.domain.languagelearning.speaking.evaluation.factory;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAssistanceUsageDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAssistantEvaluationTurnDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAudioQualitySignalsDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingEvaluationTurnDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingConversationResultDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingSttAnalysisMetadataDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingSttSegmentDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SpeakingEvaluationRequestFactory {

    public static final String EVALUATION_POLICY_VERSION =
            "speaking-evaluation-policy-v1";

    private final LanguageLearningJsonCodec jsonCodec;

    public AiSpeakingEvaluationRequestDto create(
            SpeakingSession session,
            List<SpeakingTurn> turns,
            int manualRetryAttempt
    ) {
        return new AiSpeakingEvaluationRequestDto(
                "speaking-evaluation-" + session.getId()
                        + "-retry-" + manualRetryAttempt,
                "speaking-evaluation:" + session.getId()
                        + ":" + EVALUATION_POLICY_VERSION,
                String.valueOf(session.getId()),
                session.getTopicTitle(),
                session.getPracticeMode(),
                session.getGoal(),
                session.getTopic() == null
                        ? null
                        : session.getTopic().getRecommendedLevel(),
                session.getOriginLanguage(),
                session.getLearningLanguage(),
                turns.stream().map(this::toUserTurn).toList(),
                assistantTurns(session, turns),
                session.getSessionSummary(),
                readProfile(session.getProfileSnapshotJson()),
                EVALUATION_POLICY_VERSION,
                manualRetryAttempt
        );
    }

    private List<AiSpeakingAssistantEvaluationTurnDto> assistantTurns(
            SpeakingSession session,
            List<SpeakingTurn> turns
    ) {
        java.util.ArrayList<AiSpeakingAssistantEvaluationTurnDto> result = new java.util.ArrayList<>();
        if (session.getOpeningAssistantText() != null
                && !session.getOpeningAssistantText().isBlank()) {
            result.add(toAssistantTurn(
                    "opening",
                    0,
                    session.getOpeningAssistantText(),
                    session.getOpeningConversationJson()
            ));
        }
        turns.stream()
                .filter(turn -> turn.getAssistantText() != null
                        && !turn.getAssistantText().isBlank())
                .map(turn -> toAssistantTurn(
                        String.valueOf(turn.getId()),
                        turn.getTurnIndex(),
                        turn.getAssistantText(),
                        turn.getConversationJson()
                ))
                .forEach(result::add);
        return List.copyOf(result);
    }

    private AiSpeakingAssistantEvaluationTurnDto toAssistantTurn(
            String turnId,
            int turnIndex,
            String text,
            String conversationJson
    ) {
        AiSpeakingConversationResultDto conversation = readConversation(conversationJson);
        return new AiSpeakingAssistantEvaluationTurnDto(
                turnId,
                turnIndex,
                text,
                conversation == null ? null : conversation.scriptText(),
                conversation == null || conversation.providedFacts() == null
                        ? List.of() : conversation.providedFacts(),
                conversation == null || conversation.requiredIntents() == null
                        ? List.of() : conversation.requiredIntents(),
                conversation == null || conversation.responseConstraints() == null
                        ? List.of() : conversation.responseConstraints()
        );
    }

    private AiSpeakingConversationResultDto readConversation(String json) {
        if (json == null || json.isBlank() || json.equals("{}") || json.equals("null")) {
            return null;
        }
        return jsonCodec.read(json, AiSpeakingConversationResultDto.class);
    }

    private AiSpeakingEvaluationTurnDto toUserTurn(SpeakingTurn turn) {
        List<AiSpeakingSttSegmentDto> segments = jsonCodec.read(
                turn.getSttSegmentsJson(),
                new TypeReference<List<AiSpeakingSttSegmentDto>>() {
                }
        );
        List<AiSpeakingAssistanceUsageDto> assistance = readAssistance(turn);
        AiSpeakingSttAnalysisMetadataDto metadata = readMetadata(turn);
        AiSpeakingAudioQualitySignalsDto quality = metadata == null
                ? null
                : metadata.audioQualitySignals();

        return new AiSpeakingEvaluationTurnDto(
                String.valueOf(turn.getId()),
                turn.getTurnIndex(),
                turn.getTranscript() == null ? "" : turn.getTranscript(),
                turn.getSttConfidence() == null ? 0 : turn.getSttConfidence(),
                turn.getDurationSeconds(),
                segments,
                turn.getUserAudioObjectKey(),
                turn.getUserAudioObjectKey() != null,
                quality,
                turn.isExcludedFromEvaluation(),
                assistance
        );
    }

    private List<AiSpeakingAssistanceUsageDto> readAssistance(
            SpeakingTurn turn
    ) {
        List<AssistanceType> values = jsonCodec.read(
                turn.getAssistanceUsageJson(),
                new TypeReference<List<AssistanceType>>() {
                }
        );
        return values.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value,
                        () -> new java.util.EnumMap<>(AssistanceType.class),
                        java.util.stream.Collectors.counting()
                ))
                .entrySet().stream()
                .map(entry -> new AiSpeakingAssistanceUsageDto(
                        entry.getKey(),
                        entry.getValue().intValue()
                ))
                .toList();
    }

    private AiSpeakingSttAnalysisMetadataDto readMetadata(SpeakingTurn turn) {
        if (turn.getSttMetadataJson() == null
                || turn.getSttMetadataJson().isBlank()
                || turn.getSttMetadataJson().equals("{}")) {
            return null;
        }
        return jsonCodec.read(
                turn.getSttMetadataJson(),
                AiSpeakingSttAnalysisMetadataDto.class
        );
    }

    private LearningProfileSummaryDto readProfile(String json) {
        if (json == null || json.isBlank() || json.equals("null")) {
            return null;
        }
        return jsonCodec.read(json, LearningProfileSummaryDto.class);
    }
}
