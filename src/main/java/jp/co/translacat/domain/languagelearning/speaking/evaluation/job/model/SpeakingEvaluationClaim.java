package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;

public record SpeakingEvaluationClaim(SpeakingEvaluationJobKey key, int problemIndex,
                                      String token, int manualRetryAttempt,
                                      AiSpeakingEvaluationRequestDto request) { }
