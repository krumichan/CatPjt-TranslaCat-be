package jp.co.translacat.domain.languagelearning.speaking.session.factory;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingSessionPolicySnapshotDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingSessionStartRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.topic.entity.SpeakingTopic;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpeakingSessionAiRequestFactory {

    public AiSpeakingSessionStartRequestDto create(
            SpeakingSession session,
            SpeakingSessionCreateRequestDto request,
            SpeakingTopic topic,
            LearningProfileSummaryDto profile,
            List<SelectedKeywordDto> keywords,
            SpeakingSessionPolicySnapshot policy
    ) {
        return new AiSpeakingSessionStartRequestDto(
                "speaking-session-start-" + session.getId(),
                request.idempotencyKey() + ":start",
                String.valueOf(session.getId()),
                0,
                session.getOriginLanguage(),
                session.getLearningLanguage(),
                session.getTopicTitle(),
                session.getTopicCategory(),
                clean(request.goal()),
                clean(request.persona()),
                session.getConversationStartMode(),
                topic == null ? null : topic.getRecommendedStartMode(),
                session.getCorrectionMode(),
                topic == null ? null : topic.getRecommendedLevel(),
                profile,
                keywords == null ? List.of() : keywords,
                profile == null || profile.recommendedFocus() == null
                        ? List.of()
                        : profile.recommendedFocus(),
                List.of(),
                List.of(),
                null,
                0,
                toAiPolicy(policy),
                null,
                null,
                null,
                session.getVoiceId(),
                session.getPlaybackSpeed(),
                0
        );
    }

    public AiSpeakingSessionPolicySnapshotDto toAiPolicy(
            SpeakingSessionPolicySnapshot policy
    ) {
        return new AiSpeakingSessionPolicySnapshotDto(
                policy.maxSessionMinutes(),
                policy.maxTurns(),
                policy.minValidAudioSeconds(),
                policy.maxTurnAudioSeconds(),
                policy.maxAudioFileBytes(),
                policy.automaticRetryLimitPerStage(),
                policy.manualRetryLimitPerStage()
        );
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
