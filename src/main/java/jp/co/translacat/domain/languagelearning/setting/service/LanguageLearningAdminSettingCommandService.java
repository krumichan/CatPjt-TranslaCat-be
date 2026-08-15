package jp.co.translacat.domain.languagelearning.setting.service;

import jp.co.translacat.domain.languagelearning.setting.audit.service.LanguageLearningAdminSettingAuditCommandService;
import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LanguageLearningAdminSettingCommandService {

    private final LanguageLearningAdminSettingQueryService queryService;
    private final LanguageLearningAdminSettingAuditCommandService auditCommandService;

    @Transactional
    public LanguageLearningAdminSetting update(
            AdminSettingUpdateRequestDto request
    ) {
        return update(null, request);
    }

    @Transactional
    public LanguageLearningAdminSetting update(
            Long adminUserId,
            AdminSettingUpdateRequestDto request
    ) {
        LanguageLearningAdminSetting setting = queryService.getOrCreateEntity();
        AdminSettingResponseDto before = queryService.toResponse(setting);

        setting.update(
                request.defaultDailySentenceCount(),
                request.minDailySentenceCount(),
                request.maxDailySentenceCount(),
                request.dailyKeywordMaxCount(),
                request.reviewAvailableDays(),
                request.levelRecheckRecommendationDays(),
                request.adaptiveWritingEnabled(),
                request.aiEvaluationEnabled(),
                request.speakingEnabled(),
                request.speakingEvaluationEnabled(),
                request.defaultDailySpeakingGoalMinutes(),
                request.minDailySpeakingGoalMinutes(),
                request.maxDailySpeakingGoalMinutes(),
                request.dailySpeakingHardLimitMinutes(),
                request.dailySpeakingSessionLimit(),
                request.maxSessionMinutes(),
                request.maxTurnsPerSession(),
                request.minValidAudioSeconds(),
                request.maxTurnAudioSeconds(),
                request.maxAudioFileBytes(),
                request.rawAudioRetentionDays(),
                request.reportedAudioRetentionDays(),
                request.activeSessionResumeHours(),
                request.automaticRetryLimitPerStage(),
                request.manualRetryLimitPerStage(),
                request.sttTimeoutSeconds(),
                request.ttsTimeoutSeconds(),
                request.evaluationTimeoutSeconds()
        );

        auditCommandService.record(
                adminUserId,
                before,
                queryService.toResponse(setting)
        );
        return setting;
    }
}
