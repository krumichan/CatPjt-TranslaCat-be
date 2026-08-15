package jp.co.translacat.domain.languagelearning.setting.service;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.policy.LanguageLearningUserSettingPolicy;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class LanguageLearningUserSettingCommandService {

    private final LanguageLearningUserSettingQueryService settingQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final LanguageLearningUserSettingPolicy settingPolicy;

    public LanguageLearningUserSetting update(
            Long userId,
            UserSettingUpdateRequestDto request
    ) {
        LanguageLearningAdminSetting adminSetting =
                adminSettingQueryService.getOrCreateEntity();
        LanguageLearningUserSetting setting =
                settingQueryService.getOrCreateEntity(userId);

        String originLanguage = settingPolicy.cleanLanguage(
                request.originLanguage()
        );
        String learningLanguage = settingPolicy.cleanLanguage(
                request.learningLanguage()
        );
        String timezone = settingPolicy.cleanTimezone(request.timezone());
        Integer dailySentenceCount = request.dailySentenceCount();
        Integer speakingGoal = request.dailySpeakingGoalMinutes();
        String speakingVoice = settingPolicy.cleanVoiceId(
                request.speakingVoiceId()
        );
        String playbackSpeed = settingPolicy.cleanPlaybackSpeed(
                request.speakingPlaybackSpeed()
        );

        settingPolicy.validateSentenceCount(
                dailySentenceCount,
                adminSetting
        );
        settingPolicy.validateSpeakingGoal(
                speakingGoal,
                adminSetting
        );
        settingPolicy.validateLanguagePair(
                settingPolicy.resolveNextOriginLanguage(
                        setting,
                        originLanguage
                ),
                settingPolicy.resolveNextLearningLanguage(
                        setting,
                        learningLanguage
                )
        );

        if (settingPolicy.isFirstConfiguration(setting)) {
            initializeSetting(
                    setting,
                    originLanguage,
                    learningLanguage,
                    timezone,
                    dailySentenceCount,
                    speakingGoal,
                    speakingVoice,
                    playbackSpeed
            );
            return setting;
        }

        setting.updateSpeakingPlayback(
                speakingVoice,
                playbackSpeed
        );

        LocalDate today = settingQueryService.resolveToday(setting);
        setting.scheduleUpdate(
                originLanguage,
                learningLanguage,
                timezone,
                dailySentenceCount,
                speakingGoal,
                today.plusDays(1)
        );

        return setting;
    }

    private void initializeSetting(
            LanguageLearningUserSetting setting,
            String originLanguage,
            String learningLanguage,
            String timezone,
            Integer dailySentenceCount,
            Integer speakingGoal,
            String speakingVoice,
            String playbackSpeed
    ) {
        String nextOriginLanguage =
                settingPolicy.resolveNextOriginLanguage(
                        setting,
                        originLanguage
                );
        String nextLearningLanguage =
                settingPolicy.resolveNextLearningLanguage(
                        setting,
                        learningLanguage
                );

        if (nextOriginLanguage == null || nextLearningLanguage == null) {
            throw new BusinessException(
                    "최초 학습 설정에는 Origin Language와 "
                            + "Learning Language가 모두 필요합니다.",
                    LanguageLearningErrorCode.SETTING_NOT_CONFIGURED
            );
        }

        setting.initialize(
                nextOriginLanguage,
                nextLearningLanguage,
                timezone,
                dailySentenceCount,
                speakingGoal,
                speakingVoice,
                playbackSpeed
        );
    }
}
