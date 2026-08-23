package jp.co.translacat.domain.languagelearning.listening.recommendation.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningRecommendationPolicy;
import jp.co.translacat.domain.languagelearning.listening.recommendation.entity.LearningRecommendation;
import jp.co.translacat.domain.languagelearning.listening.recommendation.model.ListeningRecommendationExplanationCommand;
import jp.co.translacat.domain.languagelearning.listening.recommendation.repository.LearningRecommendationRepository;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningRecommendationExplanationTransactionService {

    private final LearningRecommendationRepository recommendationRepository;
    private final LanguageLearningUserSettingQueryService userSettingService;
    private final ListeningPolicySettingQueryService policySettingService;
    private final ListeningOutboxTransactionService outboxTransactionService;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional(readOnly = true)
    public ExplanationWork prepare(
            ListeningOutboxTransactionService.ClaimedEvent event
    ) {
        LearningRecommendation recommendation = recommendationRepository
                .findById(event.aggregateId())
                .orElseThrow();
        var setting = userSettingService.getOrCreateEntity(
                recommendation.getUser().getId()
        );
        var policy = policySettingService.get();
        ListeningRecommendationExplanationCommand command = jsonCodec.read(
                event.payloadJson(),
                ListeningRecommendationExplanationCommand.class
        );
        String requestId = "be-listening-explanation-" + event.id();
        var request = new AiListeningContract.RecommendationExplanationRequest(
                requestId,
                event.idempotencyKey(),
                "RECOMMENDATION",
                recommendation.getTargetMetric().name(),
                recommendation.getRecommendedActivity(),
                recommendation.getRecommendedTask(),
                new AiListeningContract.RecommendationEvidenceSummary(
                        command.sources() == null
                                ? List.of()
                                : List.copyOf(command.sources()),
                        Math.max(0, command.evidenceCount()),
                        Math.max(0, Math.min(100, command.recentAverage()))
                ),
                setting.getOriginLanguage(),
                ListeningRecommendationPolicy.VERSION,
                policy.getModelConfigVersion()
        );

        return new ExplanationWork(event, recommendation.getId(), request);
    }

    @Transactional
    public void apply(
            ExplanationWork work,
            AiListeningContract.RecommendationExplanationResponse response
    ) {
        validate(work, response);
        recommendationRepository.findLockedById(work.recommendationId())
                .orElseThrow()
                .applyExplanation(
                        response.explanation(),
                        response.ctaLabel(),
                        response.explanationVersion()
                );
        outboxTransactionService.succeed(
                work.event().id(),
                LocalDateTime.now()
        );
    }

    private void validate(
            ExplanationWork work,
            AiListeningContract.RecommendationExplanationResponse response
    ) {
        var request = work.request();

        if (response == null
                || !request.requestId().equals(response.requestId())
                || !request.targetMetric().equals(response.targetMetric())
                || !request.recommendedActivity().equals(
                        response.recommendedActivity()
                )
                || !request.recommendedTask().equals(
                        response.recommendedTask()
                )
                || response.explanation() == null
                || response.explanation().isBlank()
                || response.explanation().length() > 1000
                || response.ctaLabel() == null
                || response.ctaLabel().isBlank()
                || response.ctaLabel().length() > 80
                || response.explanationVersion() == null
                || response.explanationVersion().isBlank()) {
            throw new BusinessException(
                    "AI는 Listening 추천 결정 필드를 변경할 수 없습니다.",
                    LanguageLearningErrorCode.AI_SCHEMA_INVALID
            );
        }
    }

    public record ExplanationWork(
            ListeningOutboxTransactionService.ClaimedEvent event,
            Long recommendationId,
            AiListeningContract.RecommendationExplanationRequest request
    ) {
    }
}
