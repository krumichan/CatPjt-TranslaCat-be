package jp.co.translacat.domain.languagelearning.speaking.turn.entity;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTurnStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingTurnTest {

    @Test
    void userAndAssistantAudioHaveIndependentRetention() {
        SpeakingTurn turn = turn();
        LocalDateTime userRetention = LocalDateTime.of(2026, 8, 22, 0, 0);
        LocalDateTime assistantRetention = LocalDateTime.of(2026, 8, 23, 0, 0);

        turn.markUploaded(
                "user-audio",
                "audio/webm",
                "user.webm",
                10.0,
                "[]",
                userRetention
        );
        turn.applyAssistant("hello", "assistant-audio", "audio/wav", "{}");
        turn.setAssistantAudioRetention(assistantRetention);

        turn.clearUserAudio();

        assertThat(turn.getUserAudioObjectKey()).isNull();
        assertThat(turn.getUserAudioRetentionUntil()).isNull();
        assertThat(turn.getAssistantAudioObjectKey()).isEqualTo("assistant-audio");
        assertThat(turn.getAssistantAudioRetentionUntil()).isEqualTo(assistantRetention);
    }

    @Test
    void exclusionCanBeRestoredWithoutLosingTurn() {
        SpeakingTurn turn = turn();
        turn.markUploaded(
                "user-audio",
                "audio/webm",
                "user.webm",
                10.0,
                "[]",
                LocalDateTime.now().plusDays(7)
        );
        turn.markReady("{}");

        turn.exclude();
        assertThat(turn.getStatus()).isEqualTo(SpeakingTurnStatus.EXCLUDED);
        assertThat(turn.isExcludedFromEvaluation()).isTrue();

        turn.restoreReadyAfterExcludeToggle();
        assertThat(turn.getStatus()).isEqualTo(SpeakingTurnStatus.READY);
        assertThat(turn.isExcludedFromEvaluation()).isFalse();
    }

    private SpeakingTurn turn() {
        return SpeakingTurn.createUploadGrant(
                session(),
                1,
                "turn-key",
                "upload-token",
                LocalDateTime.now().plusMinutes(10)
        );
    }

    private SpeakingSession session() {
        User user = User.createLocalUser(
                "turn@test.local",
                "pw",
                "turn",
                Role.USER,
                "TURNTEST0001"
        );
        return SpeakingSession.create(
                user,
                null,
                "session-key",
                LocalDate.of(2026, 8, 15),
                "Free Talk",
                "FREE_TALK",
                1,
                "Free Talk",
                null,
                null,
                "[]",
                "ko",
                "ja",
                ConversationStartMode.USER_FIRST,
                ConversationStartMode.USER_FIRST,
                CorrectionMode.CONVERSATION,
                5,
                20,
                "Kore",
                "NORMAL",
                "{}",
                "{}"
        );
    }
}
