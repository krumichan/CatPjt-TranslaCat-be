package jp.co.translacat.domain.chat.translation.service;

import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationCompletedEvent;
import jp.co.translacat.domain.chat.translation.port.ChatTranslationClient;
import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.enums.ChatMessageTranslationStatus;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationRequestedEvent;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageTranslationProcessor {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final ChatMessageTranslationRepository chatMessageTranslationRepository;
    private final ChatTranslationClient chatTranslationClient;
    private final ApplicationEventPublisher applicationEventPublisher;

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

        for (ChatMessageTranslation translation : pendingTranslations) {
            processPendingTranslation(translation);
        }
    }

    private void processPendingTranslation(ChatMessageTranslation translation) {
        try {
            String originalText = translation.getChatMessage().getContent();
            String targetLanguageCode = translation.getLanguageCode();

            String translatedContent = chatTranslationClient.translate(
                    originalText,
                    targetLanguageCode
            );

            translation.complete(translatedContent);

            applicationEventPublisher.publishEvent(
                    ChatMessageTranslationCompletedEvent.from(translation)
            );

            log.debug(
                    "Chat message translation completed. messageId={}, translationId={}, languageCode={}",
                    translation.getChatMessage().getId(),
                    translation.getId(),
                    translation.getLanguageCode()
            );
        } catch (Exception exception) {
            String failureReason = resolveFailureReason(exception);

            translation.fail(failureReason);

            log.warn(
                    "Chat message translation failed. translationId={}, reason={}",
                    translation.getId(),
                    failureReason,
                    exception
            );
        }
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

    private String resolveFailureReason(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        if (message.length() <= MAX_FAILURE_REASON_LENGTH) {
            return message;
        }

        return message.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}