package jp.co.translacat.domain.chat.translation.listener;

import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class ChatMessageTranslationEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageTranslationRequestedEvent event) {
        /*
         * TODO:
         *  이후 비동기 번역 처리 Issue에서 아래 흐름을 구현한다.
         *
         *  1. event.translationIds() 기준으로 PENDING 번역 row 조회
         *  2. 원문 메시지 조회
         *  3. AI/Translation API 호출
         *  4. 번역 성공 시 ChatMessageTranslation.complete(...)
         *  5. 번역 실패 시 ChatMessageTranslation.fail(...)
         *  6. WebSocket으로 chat.message.translation.completed 이벤트 발행
         */
        log.debug(
                "Chat message translation requested. chatRoomId={}, messageId={}, senderUserId={}, translationIds={}",
                event.chatRoomId(),
                event.messageId(),
                event.senderUserId(),
                event.translationIds()
        );
    }
}