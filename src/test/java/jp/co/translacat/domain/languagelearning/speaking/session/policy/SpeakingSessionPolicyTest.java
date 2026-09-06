package jp.co.translacat.domain.languagelearning.speaking.session.policy;

import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
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


    @Test
    void readAloudAndGuidedRequireAiFirstAfterResolution() {
        policy.validateResolvedStartMode(
                SpeakingPracticeMode.READ_ALOUD,
                ConversationStartMode.AI_FIRST
        );
        policy.validateResolvedStartMode(
                SpeakingPracticeMode.GUIDED,
                ConversationStartMode.AI_FIRST
        );

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> policy.validateResolvedStartMode(
                        SpeakingPracticeMode.READ_ALOUD,
                        ConversationStartMode.USER_FIRST
                ));
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> policy.validateResolvedStartMode(
                        SpeakingPracticeMode.GUIDED,
                        ConversationStartMode.USER_FIRST
                ));
    }

    @Test
    void freeSpeakingKeepsExistingStartModeChoices() {
        for (ConversationStartMode mode : ConversationStartMode.values()) {
            policy.validateResolvedStartMode(SpeakingPracticeMode.FREE, mode);
        }
    }


    @Test
    void readAloudReservesFiveProblemsAndUpToThreeAttemptsEach() {
        assertThat(policy.resolveMaxTurns(
                SpeakingPracticeMode.READ_ALOUD,
                20
        )).isEqualTo(15);
        assertThat(policy.resolveMaxTurns(
                SpeakingPracticeMode.GUIDED,
                20
        )).isEqualTo(20);
    }


    @Test
    void keywordBasedTopicRequiresAiFirstAndNoOtherTopicSource() {
        LanguageLearningAdminSetting admin = LanguageLearningAdminSetting.createDefault();

        policy.validateCreate(
                new SpeakingSessionCreateRequestDto(
                        null,
                        true,
                        null,
                        null,
                        null,
                        SpeakingPracticeMode.FREE,
                        ConversationStartMode.AI_FIRST,
                        CorrectionMode.CONVERSATION,
                        5,
                        null,
                        null,
                        "keyword-topic-ok"
                ),
                admin
        );

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> policy.validateCreate(
                        new SpeakingSessionCreateRequestDto(
                                null,
                                true,
                                null,
                                null,
                                null,
                                SpeakingPracticeMode.FREE,
                                ConversationStartMode.USER_FIRST,
                                CorrectionMode.CONVERSATION,
                                5,
                                null,
                                null,
                                "keyword-topic-user-first"
                        ),
                        admin
                ));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> policy.validateCreate(
                        new SpeakingSessionCreateRequestDto(
                                1L,
                                true,
                                null,
                                null,
                                null,
                                SpeakingPracticeMode.FREE,
                                ConversationStartMode.AI_FIRST,
                                CorrectionMode.CONVERSATION,
                                5,
                                null,
                                null,
                                "keyword-topic-duplicated"
                        ),
                        admin
                ));
    }

    private SpeakingSessionCreateRequestDto request(
            ConversationStartMode mode,
            int minutes
    ) {
        return new SpeakingSessionCreateRequestDto(
                null,
                false,
                "Free conversation",
                null,
                null,
                SpeakingPracticeMode.FREE,
                mode,
                CorrectionMode.CONVERSATION,
                minutes,
                null,
                null,
                "session-policy-test-" + mode + "-" + minutes
        );
    }
}
