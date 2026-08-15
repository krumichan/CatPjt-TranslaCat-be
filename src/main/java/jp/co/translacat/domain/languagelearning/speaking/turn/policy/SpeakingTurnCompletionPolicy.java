package jp.co.translacat.domain.languagelearning.speaking.turn.policy;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingTurnProcessResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;

import org.springframework.stereotype.Component;

@Component
public class SpeakingTurnCompletionPolicy {

    public boolean shouldComplete(
            SpeakingSession session,
            AiSpeakingTurnProcessResponseDto response,
            SpeakingSessionPolicySnapshot snapshot
    ) {
        if (!session.isActive()) {
            return false;
        }

        boolean aiRequestedEnd = response != null
                && response.conversation() != null
                && response.conversation().shouldEnd();
        boolean maxTurnsReached =
                session.getCompletedTurns() >= session.getMaxTurns();
        boolean maxTimeReached =
                session.getTotalDurationSeconds() >= snapshot.maxSessionSeconds();

        return aiRequestedEnd || maxTurnsReached || maxTimeReached;
    }
}
