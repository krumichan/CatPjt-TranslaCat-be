package jp.co.translacat.domain.chat.translation.service;

import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.enums.ChatMessageTranslationStatus;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationRequestedEvent;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageTranslationProcessor {

    private final ChatMessageTranslationRepository chatMessageTranslationRepository;

    @Transactional
    public void process(ChatMessageTranslationRequestedEvent event) {
        if (event.translationIds() == null || event.translationIds().isEmpty()) {
            log.debug(
                    "Skip chat message translation process. translationIds is empty. messageId={}",
                    event.messageId()
            );
            return;
        }

        List<ChatMessageTranslation> pendingTranslations =
                findPendingTranslations(event);

        if (pendingTranslations.isEmpty()) {
            log.debug(
                    "No pending chat message translations to process. messageId={}, translationIds={}",
                    event.messageId(),
                    event.translationIds()
            );
            return;
        }

        log.debug(
                "Pending chat message translations found. messageId={}, count={}",
                event.messageId(),
                pendingTranslations.size()
        );

        /*
         * TODO:
         *  다음 단계에서 pendingTranslations를 순회하면서 AI 번역을 수행한다.
         *
         *  1. translation.getChatMessage().getContent() 원문 조회
         *  2. translation.getLanguageCode() 대상 언어 확인
         *  3. AI 번역 Client 호출
         *  4. 성공 시 translation.complete(translatedContent)
         *  5. 실패 시 translation.fail(reason)
         *  6. 완료 후 WebSocket으로 chat.translation.completed 이벤트 발행
         */
    }

    private List<ChatMessageTranslation> findPendingTranslations(
            ChatMessageTranslationRequestedEvent event
    ) {
        List<ChatMessageTranslation> translations =
                chatMessageTranslationRepository
                        .findByIdInAndStatusAndDeletedAtIsNull(
                                event.translationIds(),
                                ChatMessageTranslationStatus.PENDING
                        );

        return translations.stream()
                .filter(translation ->
                        translation.getChatMessage() != null
                                && translation.getChatMessage().getId().equals(event.messageId())
                )
                .toList();
    }
}