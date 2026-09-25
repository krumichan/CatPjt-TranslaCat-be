package jp.co.translacat.domain.languagelearning.practice.policy;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthProfileSnapshot;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PracticeComplexityPolicy {
    private final GrowthReadGateway growth;
    private final PracticeSetRepository practiceSetRepository;

    public int resolve(Long userId, PracticeDomain domain, String mode) {
        GrowthProfileSnapshot profile = growth.snapshot(userId).profile();
        int band = baseBand(profile == null ? null : profile.baseLevelScore());
        List<PracticeSet> recent =
                practiceSetRepository.findTop5ByUserIdAndDomainAndModeAndStatusOrderByLearningDateDescIdDesc(userId,
                        domain, mode, PracticeSetStatus.COMPLETED);
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
        return Math.clamp(band, 1, 5);
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
        return domain == PracticeDomain.READING ? new int[]{1, 3, 1} : new int[]{2, 6, 2};
    }
}
