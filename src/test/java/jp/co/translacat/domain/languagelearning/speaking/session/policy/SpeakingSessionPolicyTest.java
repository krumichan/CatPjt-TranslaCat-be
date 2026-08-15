package jp.co.translacat.domain.languagelearning.speaking.session.policy;

import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SpeakingSessionPolicyTest {

    private final SpeakingSessionPolicy policy = new SpeakingSessionPolicy();

    @Test
    void acceptsAllThreeConversationStartModes() {
        LanguageLearningAdminSetting admin = LanguageLearningAdminSetting.createDefault();

        for (ConversationStartMode mode : ConversationStartMode.values()) {
            policy.validateCreate(request(mode, 5), admin);
        }
    }

    @Test
    void topicRecommendedFallsBackToAiFirst() {
        assertThat(policy.resolveStartMode(
                ConversationStartMode.TOPIC_RECOMMENDED,
                null
        )).isEqualTo(ConversationStartMode.AI_FIRST);
    }

    @Test
    void topicRecommendedUsesTopicRecommendation() {
        assertThat(policy.resolveStartMode(
                ConversationStartMode.TOPIC_RECOMMENDED,
                ConversationStartMode.USER_FIRST
        )).isEqualTo(ConversationStartMode.USER_FIRST);
    }

    @Test
    void rejectsTargetMinutesOutsideAdminBounds() {
        LanguageLearningAdminSetting admin = LanguageLearningAdminSetting.createDefault();

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> policy.validateCreate(
                        request(ConversationStartMode.AI_FIRST, 2),
                        admin
                ));
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> policy.validateCreate(
                        request(ConversationStartMode.AI_FIRST, 21),
                        admin
                ));
    }

    private SpeakingSessionCreateRequestDto request(
            ConversationStartMode mode,
            int minutes
    ) {
        return new SpeakingSessionCreateRequestDto(
                null,
                "Free conversation",
                null,
                null,
                mode,
                CorrectionMode.CONVERSATION,
                minutes,
                null,
                null,
                "session-policy-test-" + mode + "-" + minutes
        );
    }
}
