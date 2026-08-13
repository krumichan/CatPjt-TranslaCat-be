package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingGenerationContext;
import jp.co.translacat.domain.languagelearning.daily.policy.DailyWritingDifficultyPolicy;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileCommandService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyWritingGenerationContextService {

    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final LearningProfileCommandService learningProfileCommandService;
    private final DailyWritingDifficultyPolicy difficultyPolicy;

    @Transactional
    public DailyWritingGenerationContext prepare(Long userId) {
        LanguageLearningUserSetting userSetting =
                userSettingQueryService.getOrCreateEntity(userId);
        userSettingQueryService.requireConfigured(userSetting);

        LanguageLearningAdminSetting adminSetting =
                adminSettingQueryService.getOrCreateEntity();
        validateAdaptiveWritingEnabled(adminSetting);

        LocalDate learningDate = userSettingQueryService.resolveToday(userSetting);
        learningProfileCommandService.prepareDailyLearning(
                userId,
                learningDate
        );

        int sentenceCount = adminSetting.clampDailySentenceCount(
                userSetting.getDailySentenceCount()
        );
        DifficultyDistributionDto difficultyDistribution =
                difficultyPolicy.distribute(sentenceCount);

        return new DailyWritingGenerationContext(
                userSetting,
                adminSetting,
                learningDate,
                sentenceCount,
                difficultyDistribution
        );
    }

    private void validateAdaptiveWritingEnabled(
            LanguageLearningAdminSetting adminSetting
    ) {
        if (!adminSetting.isAdaptiveWritingEnabled()) {
            throw new BusinessException(
                    "Adaptive Writing이 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
    }
}
