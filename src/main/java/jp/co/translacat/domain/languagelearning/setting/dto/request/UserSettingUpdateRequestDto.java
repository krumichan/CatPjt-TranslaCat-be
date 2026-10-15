package jp.co.translacat.domain.languagelearning.setting.dto.request;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;

import java.util.List;

public record UserSettingUpdateRequestDto(
        String originLanguage,
        String learningLanguage,
        String feedbackLanguage,
        String timezone,
        Integer dailySentenceCount,
        Integer dailySpeakingGoalMinutes,
        String speakingVoiceId,
        String speakingPlaybackSpeed,
        Integer dailyListeningGoalCount,
        List<ListeningTaskType> defaultListeningTaskTypes
) {
    // 기존 호출자가 설명 언어를 보내지 않으면 LL의 현재 값 또는 최초 원문언어 기본값을 사용한다.
    public UserSettingUpdateRequestDto(
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
        this(
                originLanguage,
                learningLanguage,
                null,
                timezone,
                dailySentenceCount,
                dailySpeakingGoalMinutes,
                speakingVoiceId,
                speakingPlaybackSpeed,
                dailyListeningGoalCount,
                defaultListeningTaskTypes
        );
    }

    public UserSettingUpdateRequestDto(
            String originLanguage,
            String learningLanguage,
            String timezone,
            Integer dailySentenceCount
    ) {
        this(
                originLanguage,
                learningLanguage,
                null,
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
