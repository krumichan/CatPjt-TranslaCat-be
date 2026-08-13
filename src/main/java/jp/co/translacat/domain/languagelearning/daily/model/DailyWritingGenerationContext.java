package jp.co.translacat.domain.languagelearning.daily.model;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;

import java.time.LocalDate;

public record DailyWritingGenerationContext(
        LanguageLearningUserSetting userSetting,
        LanguageLearningAdminSetting adminSetting,
        LocalDate learningDate,
        int sentenceCount,
        DifficultyDistributionDto difficultyDistribution
) {
}
