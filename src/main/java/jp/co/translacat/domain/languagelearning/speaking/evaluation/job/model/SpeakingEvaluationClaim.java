package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingCoachingRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingResultKind;

public record SpeakingEvaluationClaim(SpeakingEvaluationJobKey key, int problemIndex,
                                      String token, int manualRetryAttempt,
                                      SpeakingResultKind resultKind,
                                      AiSpeakingEvaluationRequestDto request,
                                      AiSpeakingCoachingRequestDto coachingRequest) {
    public SpeakingEvaluationClaim(SpeakingEvaluationJobKey key, int problemIndex,
                                   String token, int manualRetryAttempt,
                                   AiSpeakingEvaluationRequestDto request) {
        this(key, problemIndex, token, manualRetryAttempt,
                SpeakingResultKind.SCORED_EVALUATION, request, null);
    }
}
