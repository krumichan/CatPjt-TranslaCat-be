package jp.co.translacat.domain.languagelearning.speaking.evaluation.policy;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingEvaluationEligibilityPolicyTest {

    private final SpeakingEvaluationEligibilityPolicy policy =
            new SpeakingEvaluationEligibilityPolicy();

    @Test
    void requiresFiveTurnsSixtySecondsAndEightyPercentStt() {
        List<SpeakingTurn> turns = validTurns(5, 12.0);

        var result = policy.evaluate(turns);

        assertThat(result.eligibleBeforeAi()).isTrue();
        assertThat(result.validUserTurns()).isEqualTo(5);
        assertThat(result.validUserSpeechSeconds()).isEqualTo(60.0);
        assertThat(result.validSttTurnRatio()).isEqualTo(1.0);
    }

    @Test
    void rejectsInsufficientSpeechSeconds() {
        var result = policy.evaluate(validTurns(5, 10.0));

        assertThat(result.eligibleBeforeAi()).isFalse();
        assertThat(result.missingRequirements())
                .contains("VALID_USER_SPEECH_SECONDS");
    }

    @Test
    void excludedTurnDoesNotDamageSttRatio() {
        List<SpeakingTurn> turns = new ArrayList<>(validTurns(5, 12.0));
        SpeakingTurn excluded = turn(6, 12.0, false);
        excluded.exclude();
        turns.add(excluded);

        var result = policy.evaluate(turns);

        assertThat(result.validSttTurnRatio()).isEqualTo(1.0);
    }

    @Test
    void confidenceThresholdIsSeventyPercent() {
        assertThat(policy.hasFormalEvaluationConfidence(0.69)).isFalse();
        assertThat(policy.hasFormalEvaluationConfidence(0.70)).isTrue();
    }

    private List<SpeakingTurn> validTurns(int count, double seconds) {
        List<SpeakingTurn> turns = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            turns.add(turn(index, seconds, true));
        }
        return turns;
    }

    private SpeakingTurn turn(int index, double seconds, boolean transcript) {
        SpeakingTurn turn = SpeakingTurn.createUploadGrant(
                session(),
                index,
                "turn-" + index,
                "token-" + index,
                LocalDateTime.now().plusMinutes(10)
        );
        turn.markUploaded(
                "audio-" + index,
                "audio/webm",
                "turn.webm",
                seconds,
                "[]",
                LocalDateTime.now().plusDays(7)
        );
        turn.markProcessing();
        if (transcript) {
            turn.applyTranscript("hello", 0.95, "[]", "{}");
        }
        turn.markReady("{}");
        return turn;
    }

    private SpeakingSession session() {
        User user = User.createLocalUser(
                "speaking@test.local",
                "pw",
                "speaking",
                Role.USER,
                "SPEAKING0001"
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
