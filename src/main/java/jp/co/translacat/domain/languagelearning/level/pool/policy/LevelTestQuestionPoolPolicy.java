package jp.co.translacat.domain.languagelearning.level.pool.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LevelTestQuestionPoolPolicy {

    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final LevelTestQuestionPoolTargetPlanner targetPlanner;

    public int targetSize() {
        return adminSettingQueryService
                .getLevelTestQuestionPoolTargetSize();
    }

    public boolean canReuse(
            long totalCount,
            long bucketCount,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand
    ) {
        int targetSize = targetSize();
        int bucketTarget = targetPlanner.bucketTarget(
                targetSize,
                domain,
                itemType,
                complexityBand
        );
        return totalCount >= targetSize
                && bucketTarget > 0
                && bucketCount >= bucketTarget;
    }
}
