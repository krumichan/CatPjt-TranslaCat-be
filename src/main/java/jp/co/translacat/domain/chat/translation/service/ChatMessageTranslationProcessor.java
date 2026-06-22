package jp.co.translacat.domain.chat.translation.service;

import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ChatMessageTranslationProcessor {

    @Transactional
    public void process(ChatMessageTranslationRequestedEvent event) {
        /*
         * TODO:
         *  다음 단계에서 아래 흐름을 구현한다.
         *
         *  1. event.translationIds() 기준으로 PENDING 번역 row 조회
         *  2. 원문 메시지 조회
         *  3. 대상 언어별 번역 처리
         *  4. 성공 시 ChatMessageTranslation.complete(...)
         *  5. 실패 시 ChatMessageTranslation.fail(...)
         *  6. WebSocket으로 chat.message.translation.completed 이벤트 발행
         */
        log.debug(
                "Start async chat message translation process. chatRoomId={}, messageId={}, senderUserId={}, translationIds={}",
                event.chatRoomId(),
                event.messageId(),
                event.senderUserId(),
                event.translationIds()
        );
    }
}