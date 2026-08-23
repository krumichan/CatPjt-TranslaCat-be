package jp.co.translacat.domain.languagelearning.setting.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningTaskSelectionPolicy;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class LanguageLearningUserSettingCommandService {

    private final LanguageLearningUserSettingQueryService settingQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final ListeningPolicySettingQueryService listeningPolicySettingQueryService;
    private final ListeningTaskSelectionPolicy listeningTaskSelectionPolicy;
    private final LanguageLearningJsonCodec jsonCodec;
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
        Integer listeningGoal = request.dailyListeningGoalCount();
        ListeningPolicySetting listeningPolicy = listeningPolicySettingQueryService.get();
        String listeningTaskTypesJson = listeningTaskTypesJson(
                request.defaultListeningTaskTypes()
        );
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
        settingPolicy.validateListeningGoal(
                listeningGoal,
                listeningPolicy.getMinItemCount(),
                listeningPolicy.getMaxItemCount()
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
                    playbackSpeed,
                    listeningGoal,
                    listeningTaskTypesJson
            );
            return setting;
        }

        setting.updateSpeakingPlayback(
                speakingVoice,
                playbackSpeed
        );
        setting.updateDefaultListeningTaskTypes(listeningTaskTypesJson);

        LocalDate today = settingQueryService.resolveToday(setting);
        LocalDate effectiveDate = today.plusDays(1);
        if (originLanguage != null
                || learningLanguage != null
                || timezone != null
                || dailySentenceCount != null
                || speakingGoal != null) {
            setting.scheduleUpdate(
                    originLanguage,
                    learningLanguage,
                    timezone,
                    dailySentenceCount,
                    speakingGoal,
                    effectiveDate
            );
        }
        setting.scheduleListeningGoal(
                listeningGoal,
                effectiveDate
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
            String playbackSpeed,
            Integer listeningGoal,
            String listeningTaskTypesJson
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
        setting.initializeListening(
                listeningGoal,
                listeningTaskTypesJson
        );
    }

    private String listeningTaskTypesJson(List<ListeningTaskType> requested) {
        if (requested == null) {
            return null;
        }
        Set<ListeningTaskType> selected = listeningTaskSelectionPolicy.validate(
                requested
        );
        List<ListeningTaskType> ordered = selected.stream()
                .sorted(Comparator.comparingInt(ListeningTaskType::ordinal))
                .toList();
        return jsonCodec.write(ordered);
    }
}
