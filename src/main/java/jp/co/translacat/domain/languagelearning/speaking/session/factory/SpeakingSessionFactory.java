package jp.co.translacat.domain.languagelearning.speaking.session.factory;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionCreationContext;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpeakingSessionFactory {

    private final LanguageLearningJsonCodec jsonCodec;

    public SpeakingSession create(
            SpeakingSessionCreateRequestDto request,
            SpeakingSessionCreationContext context
    ) {
        var setting = context.userSetting();
        var topic = context.topic();

        return SpeakingSession.create(
                context.user(),
                topic,
                request.idempotencyKey(),
                context.learningDate(),
                topic == null ? clean(request.customTopic()) : topic.getTitle(),
                topic == null ? "FREE_TALK" : topic.getCategory().name(),
                topic == null ? null : topic.getVersion(),
                clean(request.customTopic()),
                clean(request.goal()),
                clean(request.persona()),
                jsonCodec.write(context.selectedKeywords()),
                setting.getOriginLanguage(),
                setting.getLearningLanguage(),
                request.conversationStartMode(),
                context.resolvedStartMode(),
                request.correctionMode(),
                request.targetMinutes(),
                context.policySnapshot().maxTurns(),
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

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
