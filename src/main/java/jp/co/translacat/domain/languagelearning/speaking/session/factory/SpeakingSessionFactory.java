package jp.co.translacat.domain.languagelearning.speaking.session.factory;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionCreationContext;
import jp.co.translacat.domain.languagelearning.speaking.session.policy.SpeakingSessionPolicy;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SpeakingSessionFactory {

    private final LanguageLearningJsonCodec jsonCodec;
    private final SpeakingSessionPolicy sessionPolicy;

    public SpeakingSession create(
            SpeakingSessionCreateRequestDto request,
            SpeakingSessionCreationContext context
    ) {
        var setting = context.userSetting();
        var topic = context.topic();
        boolean freeSpeaking = request.practiceMode() == SpeakingPracticeMode.FREE;
        boolean keywordBasedTopic = request.keywordBasedTopic();
        String topicTitle = keywordBasedTopic
                ? keywordTopicSeed(context.selectedKeywords())
                : topic == null ? clean(request.customTopic()) : topic.getTitle();
        String topicCategory = keywordBasedTopic
                ? "KEYWORDS"
                : topic == null ? "FREE_TALK" : topic.getCategory().name();

        return SpeakingSession.create(
                context.user(),
                topic,
                request.idempotencyKey(),
                context.learningDate(),
                topicTitle,
                topicCategory,
                topic == null ? null : topic.getVersion(),
                keywordBasedTopic ? null : clean(request.customTopic()),
                freeSpeaking ? clean(request.goal()) : null,
                freeSpeaking ? clean(request.persona()) : null,
                jsonCodec.write(context.selectedKeywords()),
                setting.getOriginLanguage(),
                setting.getLearningLanguage(),
                request.practiceMode(),
                request.conversationStartMode(),
                context.resolvedStartMode(),
                request.correctionMode(),
                request.targetMinutes(),
                sessionPolicy.resolveMaxTurns(
                        request.practiceMode(),
                        context.policySnapshot().maxTurns()
                ),
                request.voiceId() == null
                        ? setting.getSpeakingVoiceId()
                        : request.voiceId(),
                request.playbackSpeed() == null
                        ? setting.getSpeakingPlaybackSpeed()
                        : request.playbackSpeed(),
                jsonCodec.write(context.policySnapshot()),
                jsonCodec.write(context.learningProfile())
        );
    }

    private String keywordTopicSeed(List<SelectedKeywordDto> keywords) {
        String seed = keywords.stream()
                .map(SelectedKeywordDto::text)
                .filter(value -> value != null && !value.isBlank())
                .limit(5)
                .collect(Collectors.joining(" · "));
        if (seed.isBlank()) {
            return "Keyword-based Speaking";
        }
        return seed.length() <= 500 ? seed : seed.substring(0, 500);
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
