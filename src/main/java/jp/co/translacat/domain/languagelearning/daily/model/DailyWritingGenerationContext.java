package jp.co.translacat.domain.languagelearning.daily.model;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.setting.model.AdminSettingsSnapshot;
import jp.co.translacat.domain.languagelearning.setting.model.UserSettingsSnapshot;

import java.time.LocalDate;

public record DailyWritingGenerationContext(
        UserSettingsSnapshot userSetting,
        AdminSettingsSnapshot adminSetting,
        LocalDate learningDate,
        int sentenceCount,
        DifficultyDistributionDto difficultyDistribution
) {
}
