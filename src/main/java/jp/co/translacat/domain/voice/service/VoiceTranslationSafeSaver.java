package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.domain.voice.entity.VoiceHistory;
import jp.co.translacat.domain.voice.entity.VoiceHistoryGroup;
import jp.co.translacat.domain.voice.model.VoiceTranslationEvent;
import jp.co.translacat.domain.voice.repository.VoiceHistoryGroupRepository;
import jp.co.translacat.domain.voice.repository.VoiceHistoryRepository;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceTranslationSafeSaver {

    private final VoiceHistoryGroupRepository historyGroupRepository;
    private final VoiceHistoryRepository historyRepository;

    private final UserService userService;

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(VoiceTranslationEvent event) {
        try {
            User user = this.userService.findByEmail(event.getUserEmail());

            VoiceHistoryGroup group = this.historyGroupRepository.findById(event.getGroupId()).orElseGet(
                    () -> this.historyGroupRepository.save(VoiceHistoryGroup.create(event.getGroupId(), user))
                );

            this.historyRepository.save(
                VoiceHistory.create(group, event.getUnit().getRawJa(), event.getUnit().getJa(), event.getUnit().getKo(), user.getEmail())
            );
        } catch (Exception e) {
            log.error("비동기 저장 중 오류 발생: {}", e.getMessage());
        }
    }
}
