package jp.co.translacat.domain.languagelearning.practice.policy;

import jp.co.translacat.domain.languagelearning.ai.dto.model.ReadingSlotTargetDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.global.exception.BusinessException;

import java.util.List;

/**
 * Reading-first product policy. Historical Vocabulary data/contracts remain readable.
 */
public final class PracticeAvailabilityPolicy {
    public static final String POLICY_VERSION = "reading-first-v1";
    public static final String VOCABULARY_RETIRED = "DAILY_VOCABULARY_RETIRED";
    public static final String B5_STRUCTURE_DEFERRED = "READING_B5_STRUCTURE_DEFERRED";

    private PracticeAvailabilityPolicy() {
    }

    public static boolean generationAllowed(PracticeDomain domain) {
        return domain != PracticeDomain.VOCABULARY;
    }

    public static void requireGenerationAllowed(PracticeDomain domain) {
        if (!generationAllowed(domain)) {
            throw new BusinessException(
                    "독립 Vocabulary 신규 생성은 종료되었습니다. 기존 이력은 유지되며 Reading에서 문맥 속 표현을 학습할 수 있습니다.",
                    VOCABULARY_RETIRED
            );
        }
    }

    /**
     * Only a new B5 STRUCTURE passage is deferred; verified private bundles remain publishable.
     */
    public static boolean needsNewB5Structure(
            AiPracticeGenerationRequestDto request, List<ReadingSlotTargetDto> targets, int order
    ) {
        if (request.domain() != PracticeDomain.READING || !"STRUCTURE".equals(request.mode())
                || targets == null || targets.size() != 5 || order < 1 || order > 5) return false;
        String passageId = order <= 3 ? "p1" : "p2";
        if (request.readingBundles() != null && request.readingBundles().containsKey(passageId)) return false;
        int last = order <= 3 ? 3 : 5;
        return targets.stream().anyMatch(target -> target.globalOrder() >= order
                && target.globalOrder() <= last && target.complexityBand() == 5);
    }

    public static boolean needsAnyNewB5Structure(
            AiPracticeGenerationRequestDto request, List<ReadingSlotTargetDto> targets, int firstMissing
    ) {
        return needsNewB5Structure(request, targets, firstMissing)
                || (firstMissing <= 4 && needsNewB5Structure(request, targets, 4));
    }

    public static void requireNewReadingAllowed(
            AiPracticeGenerationRequestDto request, List<ReadingSlotTargetDto> targets
    ) {
        if (needsAnyNewB5Structure(request, targets, 1)) {
            throw deferredB5();
        }
    }

    public static BusinessException deferredB5() {
        return new BusinessException(
                "고급 구조 분석 문제는 품질 점검 중입니다. 기존 문제와 학습 이력은 계속 이용할 수 있습니다.",
                B5_STRUCTURE_DEFERRED
        );
    }
}
