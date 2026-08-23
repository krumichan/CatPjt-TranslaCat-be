package jp.co.translacat.domain.languagelearning.listening.daily.facade;

import jp.co.translacat.domain.languagelearning.listening.attempt.service.ListeningAttemptQueryService;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetCommandService;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetQueryService;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningTtsRetryCommandService;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListeningDailySetFacade {

    private final ListeningDailySetCommandService commandService;
    private final ListeningDailySetQueryService queryService;
    private final ListeningTtsRetryCommandService ttsRetryCommandService;
    private final ListeningAttemptQueryService attemptQueryService;

    public ListeningApiContract.DailySetView getOrCreate(
            Long userId,
            ListeningApiContract.DailySetCreateRequest request
    ) {
        ListeningDailySet dailySet = commandService.getOrCreate(userId, request);

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
