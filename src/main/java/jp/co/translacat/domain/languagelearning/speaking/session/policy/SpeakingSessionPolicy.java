package jp.co.translacat.domain.languagelearning.speaking.session.policy;

import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

@Component
public class SpeakingSessionPolicy {

    public void validateCreate(
            SpeakingSessionCreateRequestDto request,
            LanguageLearningAdminSetting admin
    ) {
        if (!admin.isSpeakingEnabled()) {
            throw new BusinessException(
                    "AI Speaking 기능이 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.SPEAKING_DISABLED
            );
        }

        if (request == null) {
            throw invalid("Speaking Session 요청이 필요합니다.");
        }

        boolean topicMissing = request.topicId() == null
                && (request.customTopic() == null
                || request.customTopic().isBlank());
        boolean topicDuplicated = request.topicId() != null
                && request.customTopic() != null
                && !request.customTopic().isBlank();

        if (topicMissing || topicDuplicated) {
            throw invalid(
                    "Topic 또는 Custom Topic 중 하나만 선택해야 합니다."
            );
        }

        if (request.targetMinutes() < admin.getMinDailySpeakingGoalMinutes()
                || request.targetMinutes() > admin.getMaxDailySpeakingGoalMinutes()) {
            throw invalid(
                    "Speaking 목표 시간이 관리자 허용 범위를 벗어났습니다."
            );
        }

        if (request.conversationStartMode() == null) {
            throw invalid("Conversation Start Mode가 필요합니다.");
        }
        if (request.correctionMode() == null) {
            throw invalid("Correction Mode가 필요합니다.");
        }
        if (request.idempotencyKey() == null
                || request.idempotencyKey().isBlank()
                || request.idempotencyKey().length() > 200) {
            throw invalid("Idempotency Key가 유효하지 않습니다.");
        }

        validateVoice(request.voiceId());
        validatePlaybackSpeed(request.playbackSpeed());
        validateCustomTopic(request.customTopic());
    }

    public ConversationStartMode resolveStartMode(
            ConversationStartMode requested,
            ConversationStartMode topicRecommended
    ) {
        if (requested != ConversationStartMode.TOPIC_RECOMMENDED) {
            return requested;
        }

        if (topicRecommended == null
                || topicRecommended == ConversationStartMode.TOPIC_RECOMMENDED) {
            return ConversationStartMode.AI_FIRST;
        }

        return topicRecommended;
    }

    private void validateCustomTopic(String customTopic) {
        if (customTopic == null) {
            return;
        }

        String cleaned = customTopic.trim();
        if (cleaned.isBlank() || cleaned.length() > 500) {
            throw invalid("Custom Topic이 유효하지 않습니다.");
        }
    }

    private void validateVoice(String voiceId) {
        if (voiceId != null && voiceId.length() > 100) {
            throw invalid("Voice ID가 유효하지 않습니다.");
        }
    }

    private void validatePlaybackSpeed(String value) {
        if (value == null) {
            return;
        }

        String normalized = value.trim().toUpperCase();
        if (!normalized.equals("NORMAL") && !normalized.equals("SLOW")) {
            throw invalid("Playback Speed가 유효하지 않습니다.");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.SETTING_INVALID
        );
    }
}
