package jp.co.translacat.domain.languagelearning.speaking.evaluation.service;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.dto.response.SpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.dto.response.SpeakingMetricResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationMetricRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakingEvaluationQueryService {

    private final SpeakingEvaluationRepository evaluationRepository;
    private final SpeakingEvaluationMetricRepository metricRepository;

    public SpeakingEvaluation findLatest(Long sessionId) {
        return evaluationRepository
                .findFirstBySessionIdOrderByEvaluatedAtDesc(sessionId)
                .orElse(null);
    }

    public SpeakingEvaluationResponseDto getResponse(Long sessionId) {
        SpeakingEvaluation evaluation = findLatest(sessionId);
        return evaluation == null ? null : toResponse(evaluation);
    }

    public SpeakingEvaluationResponseDto toResponse(
            SpeakingEvaluation evaluation
    ) {
        return new SpeakingEvaluationResponseDto(
                evaluation.getId(),
                evaluation.getSession().getId(),
                evaluation.getStatus(),
                evaluation.getOverallScore(),
                evaluation.getEvaluationConfidence(),
                metricRepository
                        .findAllByEvaluationIdOrderByMetricTypeAsc(
                                evaluation.getId()
                        )
                        .stream()
                        .map(metric -> new SpeakingMetricResponseDto(
                                metric.getMetricType(),
                                metric.getState(),
                                metric.getScore(),
                                metric.getConfidence(),
                                metric.getSummary(),
                                metric.getNotEvaluableReason(),
                                metric.getEvidenceJson()
                        ))
                        .toList(),
                evaluation.getStrengthsJson(),
                evaluation.getImprovementsJson(),
                evaluation.getRecommendedExpressionsJson(),
                evaluation.getPronunciationPracticeJson(),
                evaluation.getEligibilityJson(),
                evaluation.getEvaluationVersion(),
                evaluation.getScoringPolicyVersion(),
                evaluation.getPromptVersion(),
                evaluation.getEvaluatedAt()
        );
    }
}
