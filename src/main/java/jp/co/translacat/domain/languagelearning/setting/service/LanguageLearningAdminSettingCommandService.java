package jp.co.translacat.domain.languagelearning.setting.service;

import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LanguageLearningAdminSettingCommandService {

    private final LanguageLearningAdminSettingQueryService queryService;

    @Transactional
    public LanguageLearningAdminSetting update(
            AdminSettingUpdateRequestDto request
    ) {
        LanguageLearningAdminSetting setting = queryService.getOrCreateEntity();

        setting.update(
                request.defaultDailySentenceCount(),
                request.minDailySentenceCount(),
                request.maxDailySentenceCount(),
                request.dailyKeywordMaxCount(),
                request.reviewAvailableDays(),
                request.levelRecheckRecommendationDays(),
                request.adaptiveWritingEnabled(),
                request.aiEvaluationEnabled()
        );

        return setting;
    }
}
