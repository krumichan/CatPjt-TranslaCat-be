package jp.co.translacat.domain.languagelearning.setting.dto.request;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;

import java.util.List;

public record UserSettingUpdateRequestDto(
        String originLanguage,
        String learningLanguage,
        String timezone,
        Integer dailySentenceCount,
        Integer dailySpeakingGoalMinutes,
        String speakingVoiceId,
        String speakingPlaybackSpeed,
        Integer dailyListeningGoalCount,
        List<ListeningTaskType> defaultListeningTaskTypes
) {
    public UserSettingUpdateRequestDto(
            String originLanguage,
            String learningLanguage,
            String timezone,
            Integer dailySentenceCount
    ) {
        this(
                originLanguage,
                learningLanguage,
                timezone,
                dailySentenceCount,
                null,
                null,
                null,
                null,
                null
        );
    }
}
