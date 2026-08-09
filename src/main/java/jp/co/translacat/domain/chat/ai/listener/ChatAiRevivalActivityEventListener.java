package jp.co.translacat.domain.chat.ai.listener;

import jp.co.translacat.domain.chat.ai.event.ChatAiHumanMessageRecordedEvent;
import jp.co.translacat.domain.chat.ai.service.ChatAiRevivalActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAiRevivalActivityEventListener {

    private final ChatAiRevivalActivityService activityService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatAiHumanMessageRecordedEvent event) {
        try {
            activityService.resetForHumanMessage(event);
        } catch (Exception exception) {
            log.warn(
                    "AI REVIVAL activity reset failed without affecting committed user message. messageId={}, roomId={}",
                    event == null ? null : event.messageId(),
                    event == null ? null : event.roomId(),
                    exception
            );
        }
    }
}
