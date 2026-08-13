package jp.co.translacat.domain.languagelearning.setting.service;

import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.repository.LanguageLearningAdminSettingRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LanguageLearningAdminSettingQueryService {

    private final LanguageLearningAdminSettingRepository repository;

    @Transactional
    public LanguageLearningAdminSetting getOrCreateEntity() {
        return repository.findById(LanguageLearningAdminSetting.DEFAULT_ID)
                .orElseGet(this::createDefault);
    }

    @Transactional
    public AdminSettingResponseDto getSettings() {
        LanguageLearningAdminSetting setting = repository
                .findById(LanguageLearningAdminSetting.DEFAULT_ID)
                .orElseGet(this::createDefault);

        return toResponse(setting);
    }

    public AdminSettingResponseDto toResponse(
            LanguageLearningAdminSetting setting
    ) {
        return new AdminSettingResponseDto(
                setting.getDefaultDailySentenceCount(),
                setting.getMinDailySentenceCount(),
                setting.getMaxDailySentenceCount(),
                setting.getDailyKeywordMaxCount(),
                setting.getReviewAvailableDays(),
                setting.getLevelRecheckRecommendationDays(),
                setting.isAdaptiveWritingEnabled(),
                setting.isAiEvaluationEnabled()
        );
    }

    private LanguageLearningAdminSetting createDefault() {
        return repository.save(LanguageLearningAdminSetting.createDefault());
    }
}
