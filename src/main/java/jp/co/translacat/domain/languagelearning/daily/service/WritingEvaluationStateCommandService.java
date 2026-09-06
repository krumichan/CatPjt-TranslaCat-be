package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WritingEvaluationStateCommandService {

    private final WritingEvaluationRepository evaluationRepository;

    @Transactional
    public void failIfPending(Long answerId, Throwable throwable) {
        WritingEvaluation evaluation = evaluationRepository
                .findByAnswerId(answerId)
                .orElse(null);
        if (evaluation == null
                || evaluation.getStatus() != EvaluationStatus.PENDING) {
            return;
        }

        evaluation.fail(trimMessage(throwable == null ? null : throwable.getMessage()));
        evaluationRepository.save(evaluation);
    }

    private String trimMessage(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        return message.length() <= 1000
                ? message
                : message.substring(0, 1000);
    }
}
