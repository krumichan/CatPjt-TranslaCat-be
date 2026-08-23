package jp.co.translacat.domain.languagelearning.listening.recommendation.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningRecommendationStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningWeaknessState;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningRecommendationPolicy;
import jp.co.translacat.domain.languagelearning.listening.recommendation.entity.LearningRecommendation;
import jp.co.translacat.domain.languagelearning.listening.recommendation.model.ListeningRecommendationExplanationCommand;
import jp.co.translacat.domain.languagelearning.listening.recommendation.repository.LearningRecommendationRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningRecommendationCommandService {

    private final LearningRecommendationRepository recommendationRepository;
    private final ListeningRecommendationPolicy recommendationPolicy;
    private final ListeningOutboxCommandService outboxCommandService;
    private final UserRepository userRepository;

    @Transactional
    public void refresh(
            Long userId,
            String learningLanguage,
            List<ListeningApiContract.MetricProfileView> profiles
    ) {
        User user = userRepository.getReferenceById(userId);
        List<ListeningApiContract.MetricProfileView> weak = profiles.stream()
                .filter(value -> value.weaknessState()
                        == ListeningWeaknessState.ACTIVE)
                .filter(value -> recommendationPolicy.target(value.metric())
                        != null)
                .sorted(Comparator.comparing(
                        value -> value.score() == null ? 101 : value.score()
                ))
                .limit(2)
                .toList();
        LocalDateTime now = LocalDateTime.now();
        var selectedMetrics = weak.stream()
                .map(ListeningApiContract.MetricProfileView::metric)
                .collect(() -> EnumSet.noneOf(ListeningProfileMetric.class),
                        EnumSet::add, EnumSet::addAll);

        recommendationRepository
                .findAllByUserIdAndLearningLanguageAndStatus(
                        userId,
                        learningLanguage,
                        ListeningRecommendationStatus.ACTIVE
                )
                .forEach(value -> {
                    if (value.getExpiresAt().isBefore(now)) {
                        value.expire(now);
                    } else if (!selectedMetrics.contains(value.getTargetMetric())) {
                        value.resolve();
                    }
                });

        int priority = 1;

        for (var profile : weak) {
            var target = recommendationPolicy.target(profile.metric());
            String reason = profile.metric().name()
                    + " 최근 근거가 약점 기준(<65)에 해당합니다.";
            var existing = recommendationRepository
                    .findByUserIdAndLearningLanguageAndTargetMetricAndCalculationVersion(
                            userId,
                            learningLanguage,
                            profile.metric(),
                            ListeningRecommendationPolicy.VERSION
                    );
            LearningRecommendation recommendation;

            if (existing.isPresent()) {
                recommendation = existing.get();
                recommendation.refresh(
                        reason,
                        "[]",
                        priority,
                        now.plusDays(7)
                );
            } else {
                recommendation = recommendationRepository.saveAndFlush(
                        LearningRecommendation.create(
                                user,
                                learningLanguage,
                                profile.metric(),
                                target.activity(),
                                target.task(),
                                reason,
                                "[]",
                                priority,
                                now.plusDays(7),
                                ListeningRecommendationPolicy.VERSION
                        )
                );
            }

            enqueueExplanation(recommendation, profile);
            priority++;
        }
    }

    @Transactional
    public void dismiss(Long userId, Long recommendationId) {
        recommendationRepository.findByIdAndUserId(recommendationId, userId)
                .orElseThrow()
                .dismiss(LocalDateTime.now());
    }

    private void enqueueExplanation(
            LearningRecommendation recommendation,
            ListeningApiContract.MetricProfileView profile
    ) {
        if (recommendation.getStatus()
                != ListeningRecommendationStatus.ACTIVE) {
            return;
        }

        outboxCommandService.enqueue(
                ListeningOutboxType.EXPLAIN_RECOMMENDATION,
                recommendation.getId(),
                new ListeningRecommendationExplanationCommand(
                        List.of("LISTENING"),
                        profile.sampleCount(),
                        profile.score() == null ? 0 : profile.score()
                ),
                "listening:recommendation:"
                        + recommendation.getId()
                        + ":explanation:"
                        + ListeningRecommendationPolicy.VERSION
        );
    }
}
