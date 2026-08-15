package jp.co.translacat.domain.languagelearning.speaking.turn.policy;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingTurnCompletionPolicyTest {

    private final SpeakingTurnCompletionPolicy policy =
            new SpeakingTurnCompletionPolicy();

    @Test
    void completesWhenMaxTurnCountIsReached() {
        SpeakingSession session = session(2);
        session.registerCompletedTurn(10, null, null);
        session.registerCompletedTurn(10, null, null);

        assertThat(policy.shouldComplete(session, null, snapshot(10))).isTrue();
    }

    @Test
    void completesWhenMaxSessionTimeIsReached() {
        SpeakingSession session = session(20);
        session.registerCompletedTurn(60, null, null);

        assertThat(policy.shouldComplete(session, null, snapshot(1))).isTrue();
    }

    @Test
    void staysActiveBelowLimitsWithoutAiEndSignal() {
        SpeakingSession session = session(20);
        session.registerCompletedTurn(20, null, null);

        assertThat(policy.shouldComplete(session, null, snapshot(10))).isFalse();
    }

    private SpeakingSession session(int maxTurns) {
        User user = User.createLocalUser(
                "completion@test.local",
                "pw",
                "completion",
                Role.USER,
                "COMPLETE0001"
        );
        return SpeakingSession.create(
                user,
                null,
                "completion-key",
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
                maxTurns,
                "Kore",
                "NORMAL",
                "{}",
                "{}"
        );
    }

    private SpeakingSessionPolicySnapshot snapshot(int maxMinutes) {
        return new SpeakingSessionPolicySnapshot(
                true,
                30,
                5,
                maxMinutes,
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
