package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.stereotype.Service;

/**
 * 완료 기준점과 Profile은 모두 LL에 있다. 더 이상 Core Profile을 만들거나 복제하지 않는다.
 */
@Service
public class LevelTestBaselineBridge {
    private final GrowthReadGateway growth;

    public LevelTestBaselineBridge(GrowthReadGateway growth) {
        this.growth = growth;
    }

    public LearningProfileState synchronize(Long userId) {
        var profile = growth.snapshot(userId).profile();
        return profile == null || profile.baselineCompletionId() == null ? null : profile.state();
    }

    public void requireCompleted(Long userId) {
        if (synchronize(userId) == null)
            throw new BusinessException("최초 Level Test가 필요합니다.", LanguageLearningErrorCode.LEVEL_TEST_REQUIRED);
    }
}
