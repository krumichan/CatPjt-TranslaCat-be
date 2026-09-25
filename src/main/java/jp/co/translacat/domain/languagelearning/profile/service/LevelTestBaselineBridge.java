package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.level.port.LevelTestGateway;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.stereotype.Service;

/**
 * 수신 실패 시 다음 조회/학습 준비에서 동일 완료 ID로 재시도한다. 이전 Core 레벨을 대신 반환하지 않는다.
 */
@Service
public class LevelTestBaselineBridge {
    private final LevelTestGateway gateway;
    private final LevelTestBaselineApplicationService application;

    public LevelTestBaselineBridge(LevelTestGateway gateway, LevelTestBaselineApplicationService application) {
        this.gateway = gateway;
        this.application = application;
    }

    public LearningProfileState synchronize(Long userId) {
        return gateway.baseline(userId).map(value -> application.apply(userId, value)).orElse(null);
    }

    public void requireCompleted(Long userId) {
        if (synchronize(userId) == null)
            throw new BusinessException("최초 Level Test가 필요합니다.", LanguageLearningErrorCode.LEVEL_TEST_REQUIRED);
    }
}
