package jp.co.translacat.domain.languagelearning.setting.dto.request;

public record UserSettingUpdateRequestDto(
        String originLanguage,
        String learningLanguage,
        String timezone,
        Integer dailySentenceCount,
        Integer dailySpeakingGoalMinutes,
        String speakingVoiceId,
        String speakingPlaybackSpeed
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
                null
        );
    }
}
