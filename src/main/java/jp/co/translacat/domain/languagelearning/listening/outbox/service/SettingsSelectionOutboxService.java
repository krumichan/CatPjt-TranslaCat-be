package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class SettingsSelectionOutboxService {
    private final UserSettingsGateway settings;
    private final ListeningOutboxEventRepository events;
    private final ListeningOutboxCommandService outbox;
    public SettingsSelectionOutboxService(UserSettingsGateway settings, ListeningOutboxEventRepository events,
                                         ListeningOutboxCommandService outbox) {
        this.settings = settings; this.events = events; this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long userId, Long sessionId, List<ListeningTaskType> ordered) {
        String key = "settings-selection:session:" + sessionId;
        // 세션의 idempotent 재조회가 더 최근의 직접 설정을 되돌리지 않게 한다.
        if (events.findByIdempotencyKey(key).isPresent()) return;
        var snapshot = settings.getSnapshot(userId);
        settings.requireConfigured(snapshot);
        outbox.enqueue(ListeningOutboxType.REMEMBER_SETTINGS_SELECTION, sessionId,
                new SettingsSelectionPayload(userId, snapshot.revision().toString(), List.copyOf(ordered)), key);
    }
}
