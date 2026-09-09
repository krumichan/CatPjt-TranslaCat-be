package jp.co.translacat.domain.languagelearning.listening.daily.facade;

import jp.co.translacat.domain.languagelearning.listening.attempt.service.ListeningAttemptQueryService;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetCommandService;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetQueryService;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningGenerationRetryCommandService;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningTtsRetryCommandService;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningDailySetFacade {

    private final ListeningDailySetCommandService commandService;
    private final ListeningDailySetQueryService queryService;
    private final ListeningGenerationRetryCommandService generationRetryCommandService;
    private final ListeningTtsRetryCommandService ttsRetryCommandService;
    private final ListeningAttemptQueryService attemptQueryService;
    private final LanguageLearningUserSettingQueryService userSettingService;

    public ListeningApiContract.DailySetView getOrCreate(
            Long userId,
            ListeningApiContract.DailySetCreateRequest request
    ) {
        ListeningDailySet dailySet = commandService.getOrCreate(userId, request);

        return queryService.view(userId, dailySet);
    }

    public ListeningApiContract.DailySetView get(Long userId, Long dailySetId) {
        return queryService.view(userId, queryService.owned(userId, dailySetId));
    }

    public List<ListeningApiContract.DailyModeStatusView> todayStatuses(Long userId) {
        var setting = userSettingService.getOrCreateEntity(userId);
        userSettingService.requireConfigured(setting);
        return queryService.todayStatuses(
                userId,
                userSettingService.resolveToday(setting),
                setting.getLearningLanguage()
        );
    }

    public ListeningApiContract.DailySetView retryGeneration(
            Long userId,
            Long dailySetId
    ) {
        ListeningDailySet dailySet = generationRetryCommandService.retry(
                userId,
                dailySetId
        );

        return queryService.view(userId, dailySet);
    }

    public ListeningApiContract.DailySetView retryTts(
            Long userId,
            Long itemId
    ) {
        ListeningDailySet dailySet = ttsRetryCommandService.retry(
                userId,
                itemId
        );

        return queryService.view(userId, dailySet);
    }

    public ListeningAudioObject referenceAudio(Long userId, Long itemId) {
        return attemptQueryService.referenceAudio(userId, itemId);
    }

    public ListeningApiContract.PolicyView policy() {
        return queryService.policy();
    }
}
