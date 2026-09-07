package jp.co.translacat.domain.languagelearning.practice.policy;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfile;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PracticeComplexityPolicy {
    private final LearningProfileRepository profileRepository;
    private final PracticeSetRepository practiceSetRepository;

    public int resolve(Long userId, PracticeDomain domain, String mode) {
        LearningProfile profile = profileRepository.findByUserId(userId).orElse(null);
        int band = baseBand(profile == null ? null : profile.getBaseLevelScore());
        List<PracticeSet> recent = practiceSetRepository
                .findTop5ByUserIdAndDomainAndModeAndStatusOrderByLearningDateDescIdDesc(
                        userId, domain, mode, PracticeSetStatus.COMPLETED
                );
        double average = recent.stream()
                .map(PracticeSet::getOfficialScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);
        if (!Double.isNaN(average)) {
            if (average >= 85) {
                band++;
            } else if (average < 55) {
                band--;
            }
        }
        return Math.max(1, Math.min(5, band));
    }

    public int baseBand(Double score) {
        if (score == null) return 3;
        if (score < 40) return 1;
        if (score < 55) return 2;
        if (score < 70) return 3;
        if (score < 85) return 4;
        return 5;
    }

    public int[] mix(PracticeDomain domain) {
        return domain == PracticeDomain.READING
                ? new int[]{1, 3, 1}
                : new int[]{2, 6, 2};
    }
}
