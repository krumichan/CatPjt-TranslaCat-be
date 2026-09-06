package jp.co.translacat.domain.languagelearning.speaking.evaluation.policy;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingEvaluationEligibilityDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTurnStatus;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.session.policy.SpeakingSessionPolicy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpeakingEvaluationEligibilityPolicy {

    public static final int REQUIRED_USER_TURNS = 5;
    public static final double REQUIRED_SPEECH_SECONDS = 60.0;
    public static final double REQUIRED_STT_RATIO = 0.80;
    public static final double REQUIRED_EVALUATION_CONFIDENCE = 0.70;

    public AiSpeakingEvaluationEligibilityDto evaluate(
            SpeakingPracticeMode practiceMode,
            List<SpeakingTurn> turns
    ) {
        boolean readAloud = practiceMode == SpeakingPracticeMode.READ_ALOUD;
        int requiredUserTurns = readAloud
                ? SpeakingSessionPolicy.READ_ALOUD_DAILY_ITEM_COUNT
                * SpeakingSessionPolicy.READ_ALOUD_REQUIRED_ATTEMPTS_PER_ITEM
                : REQUIRED_USER_TURNS;
        double requiredSpeechSeconds = readAloud
                ? 0.0
                : REQUIRED_SPEECH_SECONDS;
        List<SpeakingTurn> included = turns.stream()
                .filter(turn -> !turn.isExcludedFromEvaluation())
                .filter(turn -> turn.getStatus()
                        != SpeakingTurnStatus.AWAITING_UPLOAD)
                .toList();
        List<SpeakingTurn> validSttTurns = included.stream()
                .filter(turn -> turn.getTranscript() != null)
                .filter(turn -> !turn.getTranscript().isBlank())
                .toList();

        int validTurns = validSttTurns.size();
        double speechSeconds = validSttTurns.stream()
                .mapToDouble(SpeakingTurn::getDurationSeconds)
                .sum();
        double sttRatio = included.isEmpty()
                ? 0
                : (double) validSttTurns.size() / included.size();
        List<String> missing = new ArrayList<>();
        if (validTurns < requiredUserTurns) {
            missing.add("VALID_USER_TURNS");
        }
        if (speechSeconds < requiredSpeechSeconds) {
            missing.add("VALID_USER_SPEECH_SECONDS");
        }
        if (sttRatio < REQUIRED_STT_RATIO) {
            missing.add("VALID_STT_TURN_RATIO");
        }

        return new AiSpeakingEvaluationEligibilityDto(
                validTurns,
                round(speechSeconds),
                round(sttRatio),
                requiredUserTurns,
                requiredSpeechSeconds,
                REQUIRED_STT_RATIO,
                REQUIRED_EVALUATION_CONFIDENCE,
                missing.isEmpty(),
                List.copyOf(missing)
        );
    }

    public AiSpeakingEvaluationEligibilityDto evaluate(
            List<SpeakingTurn> turns
    ) {
        return evaluate(SpeakingPracticeMode.FREE, turns);
    }

    public boolean hasFormalEvaluationConfidence(Double confidence) {
        return confidence != null
                && confidence >= REQUIRED_EVALUATION_CONFIDENCE;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
