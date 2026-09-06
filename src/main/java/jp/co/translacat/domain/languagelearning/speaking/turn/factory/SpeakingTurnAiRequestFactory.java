package jp.co.translacat.domain.languagelearning.speaking.turn.factory;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAssistanceUsageDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingConversationMessageDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingSessionPolicySnapshotDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingSttAnalysisMetadataDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingSttSegmentDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingTranscriptDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingTurnProcessRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.session.policy.SpeakingSessionPolicy;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.factory.SpeakingSessionAiRequestFactory;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SpeakingTurnAiRequestFactory {

    private final LanguageLearningJsonCodec jsonCodec;
    private final SpeakingSessionAiRequestFactory sessionAiRequestFactory;

    public AiSpeakingTurnProcessRequestDto create(
            SpeakingSession session,
            SpeakingTurn turn,
            List<SpeakingTurn> history,
            List<AssistanceType> assistanceTypes
    ) {
        return create(
                session,
                turn,
                history,
                assistanceTypes,
                false,
                null
        );
    }

    public AiSpeakingTurnProcessRequestDto create(
            SpeakingSession session,
            SpeakingTurn turn,
            List<SpeakingTurn> history,
            List<AssistanceType> assistanceTypes,
            boolean forceStt,
            Double durationSecondsOverride
    ) {
        SpeakingSessionPolicySnapshot policy = jsonCodec.read(
                session.getPolicySnapshotJson(),
                SpeakingSessionPolicySnapshot.class
        );
        LearningProfileSummaryDto profile = readNullable(
                session.getProfileSnapshotJson(),
                LearningProfileSummaryDto.class
        );
        List<SelectedKeywordDto> keywords = jsonCodec.read(
                session.getSelectedKeywordsJson(),
                new TypeReference<List<SelectedKeywordDto>>() {
                }
        );

        return new AiSpeakingTurnProcessRequestDto(
                "speaking-turn-" + turn.getId()
                        + "-recording-" + turn.getRecordingRevision()
                        + "-retry-" + turn.getManualRetryCount(),
                turn.getIdempotencyKey() + ":recording:"
                        + turn.getRecordingRevision()
                        + ":process:" + turn.getManualRetryCount(),
                String.valueOf(session.getId()),
                turn.getTurnIndex(),
                turn.getProblemIndex(),
                turn.getAttemptIndex(),
                shouldGenerateNextReadAloudProblem(session, turn),
                session.getOriginLanguage(),
                session.getLearningLanguage(),
                session.getTopicTitle(),
                session.getPracticeMode(),
                session.getTopicCategory(),
                session.getGoal(),
                session.getPersona(),
                session.getConversationStartMode(),
                session.getTopic() == null
                        ? null
                        : session.getTopic().getRecommendedStartMode(),
                session.getCorrectionMode(),
                session.getTopic() == null
                        ? null
                        : session.getTopic().getRecommendedLevel(),
                profile,
                keywords,
                profile == null || profile.recommendedFocus() == null
                        ? List.of()
                        : profile.recommendedFocus(),
                conversationHistory(history),
                assistanceUsage(assistanceTypes),
                session.getSessionSummary(),
                session.getTotalDurationSeconds(),
                sessionAiRequestFactory.toAiPolicy(
                        policy,
                        session.getMaxTurns()
                ),
                turn.getUserAudioObjectKey(),
                turn.getUserAudioContentType(),
                durationSecondsOverride == null
                        ? turn.getDurationSeconds()
                        : durationSecondsOverride,
                session.getVoiceId(),
                session.getPlaybackSpeed(),
                turn.getManualRetryCount(),
                forceStt ? null : existingTranscript(turn),
                turn.getTurnIndex() == 1
        );
    }

    private boolean shouldGenerateNextReadAloudProblem(
            SpeakingSession session,
            SpeakingTurn turn
    ) {
        return session.getPracticeMode() == SpeakingPracticeMode.READ_ALOUD
                && turn.getProblemIndex() != null
                && turn.getAttemptIndex() != null
                && turn.getAttemptIndex() == SpeakingSessionPolicy.READ_ALOUD_REQUIRED_ATTEMPTS_PER_ITEM
                && turn.getProblemIndex() < SpeakingSessionPolicy.READ_ALOUD_DAILY_ITEM_COUNT;
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

    private List<AiSpeakingAssistanceUsageDto> assistanceUsage(
            List<AssistanceType> values
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Map<AssistanceType, Integer> counts = new EnumMap<>(AssistanceType.class);
        for (AssistanceType value : values) {
            if (value != null) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new AiSpeakingAssistanceUsageDto(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    private AiSpeakingTranscriptDto existingTranscript(SpeakingTurn turn) {
        if (turn.getTranscript() == null || turn.getTranscript().isBlank()) {
            return null;
        }
        List<AiSpeakingSttSegmentDto> segments = jsonCodec.read(
                turn.getSttSegmentsJson(),
                new TypeReference<List<AiSpeakingSttSegmentDto>>() {
                }
        );
        AiSpeakingSttAnalysisMetadataDto metadata = jsonCodec.read(
                turn.getSttMetadataJson(),
                AiSpeakingSttAnalysisMetadataDto.class
        );
        return new AiSpeakingTranscriptDto(
                turn.getTranscript(),
                sessionLanguage(turn),
                turn.getSttConfidence() == null ? 0 : turn.getSttConfidence(),
                turn.getSttConfidence() == null || turn.getSttConfidence() < 0.5,
                segments,
                metadata
        );
    }

    private String sessionLanguage(SpeakingTurn turn) {
        return turn.getSession().getLearningLanguage();
    }

    private <T> T readNullable(String json, Class<T> type) {
        if (json == null || json.isBlank() || json.equals("null")) {
            return null;
        }
        return jsonCodec.read(json, type);
    }
}
