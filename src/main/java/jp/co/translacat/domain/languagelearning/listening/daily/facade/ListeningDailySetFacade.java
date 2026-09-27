package jp.co.translacat.domain.languagelearning.listening.daily.facade;

import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.port.ListeningGateway;
import jp.co.translacat.domain.languagelearning.listening.setting.port.ListeningPolicyGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningDailySetFacade {
    private final ListeningGateway gateway;
    private final ListeningPolicyGateway policyGateway;

    public ListeningApiContract.DailySetView getOrCreate(Long userId,
                                                         ListeningApiContract.DailySetCreateRequest request) {
        return gateway.post(userId, "/daily-sets", request, ListeningApiContract.DailySetView.class);
    }

    public ListeningApiContract.DailySetView get(Long userId, Long setId) {
        return gateway.get(userId, "/daily-sets/" + setId, ListeningApiContract.DailySetView.class);
    }

    public List<ListeningApiContract.DailyModeStatusView> todayStatuses(Long userId) {
        return gateway.list(userId, "/today/status", ListeningApiContract.DailyModeStatusView.class);
    }

    public ListeningApiContract.DailySetView retryGeneration(Long userId, Long setId) {
        return gateway.post(userId, "/daily-sets/" + setId + "/retry-generation", null,
                ListeningApiContract.DailySetView.class);
    }

    public ListeningApiContract.DailySetView retryTts(Long userId, Long itemId) {
        return gateway.post(userId, "/items/" + itemId + "/retry-tts", null, ListeningApiContract.DailySetView.class);
    }

    public ListeningAudioObject referenceAudio(Long userId, Long itemId) {
        return gateway.audio(userId, "/items/" + itemId + "/audio");
    }

    public ListeningApiContract.PolicyView policy() {
        var value = policyGateway.get();
        return new ListeningApiContract.PolicyView(value.enabled(), value.defaultItemCount(), value.minItemCount(),
                value.maxItemCount(), value.hardItemLimit(), value.resumeHours(), value.referenceAudioRetentionDays(),
                value.userAudioRetentionDays(), value.reportedAudioRetentionDays(), value.automaticRetryLimit(),
                value.manualRetryLimit(), value.practiceAttemptLimit(), value.profilePolicyVersion(),
                value.modelConfigVersion(), value.referenceTtsRegenerationEnabled());
    }
}
