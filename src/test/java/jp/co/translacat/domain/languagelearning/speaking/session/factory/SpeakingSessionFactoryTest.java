package jp.co.translacat.domain.languagelearning.speaking.session.factory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionCreationContext;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.session.policy.SpeakingSessionPolicy;
import jp.co.translacat.domain.user.entity.User;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpeakingSessionFactoryTest {

    private final SpeakingSessionFactory factory = new SpeakingSessionFactory(
            new LanguageLearningJsonCodec(new ObjectMapper()),
            new SpeakingSessionPolicy()
    );

    @Test
    void ignoresGoalAndPersonaOutsideFreeSpeaking() {
        SpeakingSession session = factory.create(
                request(SpeakingPracticeMode.GUIDED, " 목표 ", " 호텔 직원 "),
                context()
        );

        assertThat(session.getGoal()).isNull();
        assertThat(session.getPersona()).isNull();
    }

    @Test
    void keepsGoalAndPersonaForFreeSpeaking() {
        SpeakingSession session = factory.create(
                request(SpeakingPracticeMode.FREE, " 목표 ", " 호텔 직원 "),
                context()
        );

        assertThat(session.getGoal()).isEqualTo("목표");
        assertThat(session.getPersona()).isEqualTo("호텔 직원");
    }


    @Test
    void readAloudUsesFiveItemsWithTwoRequiredAndAtMostThreeAttemptsEach() {
        SpeakingSession session = factory.create(
                request(SpeakingPracticeMode.READ_ALOUD, null, null),
                context()
        );

        assertThat(SpeakingSessionPolicy.READ_ALOUD_DAILY_ITEM_COUNT).isEqualTo(5);
        assertThat(SpeakingSessionPolicy.READ_ALOUD_REQUIRED_ATTEMPTS_PER_ITEM).isEqualTo(2);
        assertThat(SpeakingSessionPolicy.READ_ALOUD_MAX_ATTEMPTS_PER_ITEM).isEqualTo(3);
        assertThat(session.getMaxTurns()).isEqualTo(15);
    }

    @Test
    void createsKeywordBasedTopicSeedAndCategory() {
        SpeakingSession session = factory.create(
                new SpeakingSessionCreateRequestDto(
                        null,
                        true,
                        null,
                        null,
                        null,
                        SpeakingPracticeMode.GUIDED,
                        ConversationStartMode.AI_FIRST,
                        CorrectionMode.CONVERSATION,
                        5,
                        "Kore",
                        "NORMAL",
                        "speaking-keyword-session-test"
                ),
                context(List.of(
                        new SelectedKeywordDto(
                                "SYSTEM:1",
                                "travel",
                                KeywordSource.SYSTEM,
                                KeywordType.TOPIC,
                                "TRAVEL",
                                null
                        ),
                        new SelectedKeywordDto(
                                "CUSTOM:2",
                                "hotel",
                                KeywordSource.CUSTOM,
                                KeywordType.VOCABULARY,
                                "HOTEL",
                                null
                        )
                ))
        );

        assertThat(session.getTopicTitle()).isEqualTo("travel · hotel");
        assertThat(session.getTopicCategory()).isEqualTo("KEYWORDS");
        assertThat(session.getCustomTopic()).isNull();
    }

    private SpeakingSessionCreateRequestDto request(
            SpeakingPracticeMode practiceMode,
            String goal,
            String persona
    ) {
        return new SpeakingSessionCreateRequestDto(
                null,
                false,
                "호텔 체크인",
                goal,
                persona,
                practiceMode,
                ConversationStartMode.AI_FIRST,
                CorrectionMode.CONVERSATION,
                5,
                "Kore",
                "NORMAL",
                "speaking-session-test"
        );
    }

    private SpeakingSessionCreationContext context() {
        return context(List.of());
    }

    private SpeakingSessionCreationContext context(List<SelectedKeywordDto> keywords) {
        LanguageLearningUserSetting setting = mock(LanguageLearningUserSetting.class);
        when(setting.getOriginLanguage()).thenReturn("ko");
        when(setting.getLearningLanguage()).thenReturn("ja");
        when(setting.getSpeakingVoiceId()).thenReturn("Kore");
        when(setting.getSpeakingPlaybackSpeed()).thenReturn("NORMAL");

        return new SpeakingSessionCreationContext(
                mock(User.class),
                setting,
                LocalDate.of(2026, 9, 6),
                null,
                ConversationStartMode.AI_FIRST,
                policySnapshot(),
                null,
                keywords
        );
    }

    private SpeakingSessionPolicySnapshot policySnapshot() {
        return new SpeakingSessionPolicySnapshot(
                true,
                30,
                5,
                10,
                20,
                1.0,
                60,
                10L * 1024L * 1024L,
                7,
                30,
                2,
                2,
                1,
                30,
                30,
                60
        );
    }
}
