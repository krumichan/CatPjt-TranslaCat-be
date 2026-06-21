package jp.co.translacat.domain.chat.translation.repository;

import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.enums.ChatMessageTranslationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageTranslationRepository extends JpaRepository<ChatMessageTranslation, Long> {

    Optional<ChatMessageTranslation> findByIdAndDeletedAtIsNull(Long id);

    Optional<ChatMessageTranslation> findByChatMessageAndLanguageCodeAndDeletedAtIsNull(
            ChatMessage chatMessage,
            String languageCode
    );

    Optional<ChatMessageTranslation> findByChatMessageIdAndLanguageCodeAndDeletedAtIsNull(
            Long chatMessageId,
            String languageCode
    );

    List<ChatMessageTranslation> findByChatMessageIdAndDeletedAtIsNull(
            Long chatMessageId
    );

    List<ChatMessageTranslation> findByChatMessageIdInAndDeletedAtIsNull(
            Collection<Long> chatMessageIds
    );

    List<ChatMessageTranslation> findByStatusAndDeletedAtIsNull(
            ChatMessageTranslationStatus status
    );

    List<ChatMessageTranslation> findTop100ByStatusAndDeletedAtIsNullOrderByIdAsc(
            ChatMessageTranslationStatus status
    );

    boolean existsByChatMessageIdAndLanguageCodeAndDeletedAtIsNull(
            Long chatMessageId,
            String languageCode
    );
}