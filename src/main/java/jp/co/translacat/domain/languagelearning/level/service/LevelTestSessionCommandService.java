package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileCommandService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LevelTestSessionCommandService {

    private final LevelTestSessionRepository sessionRepository;
    private final LevelTestQueryService levelTestQueryService;
    private final LearningProfileCommandService profileCommandService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final UserRepository userRepository;

    public LevelTestSession start(
            Long userId,
            LevelTestSessionType sessionType
    ) {
        validateAiEvaluationEnabled();

        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        userSettingQueryService.requireConfigured(setting);

        var activeSession = levelTestQueryService.getActiveSession(userId);
        if (activeSession.isPresent()) {
            return activeSession.get();
        }

        validateInitialTestState(userId, sessionType);
        prepareRecheck(userId, sessionType);

        return sessionRepository.save(
                LevelTestSession.start(
                        getUser(userId),
                        sessionType
                )
        );
    }

    private void validateInitialTestState(
            Long userId,
            LevelTestSessionType sessionType
    ) {
        if (sessionType != LevelTestSessionType.INITIAL) {
            return;
        }
        if (!levelTestQueryService.hasCompletedInitialTest(userId)) {
            return;
        }

        throw new BusinessException(
                "최초 Level Test가 이미 완료되었습니다.",
                LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
        );
    }

    private void prepareRecheck(
            Long userId,
            LevelTestSessionType sessionType
    ) {
        if (sessionType == LevelTestSessionType.RECHECK) {
            profileCommandService.resetForRecheck(userId);
        }
    }

    private void validateAiEvaluationEnabled() {
        if (!adminSettingQueryService
                .getOrCreateEntity()
                .isAiEvaluationEnabled()) {
            throw new BusinessException(
                    "AI Writing 평가가 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
    }
}
