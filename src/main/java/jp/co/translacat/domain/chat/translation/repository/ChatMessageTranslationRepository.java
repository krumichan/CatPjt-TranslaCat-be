package jp.co.translacat.domain.chat.translation.repository;

import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.enums.ChatMessageTranslationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageTranslationRepository extends JpaRepository<ChatMessageTranslation, Long> {

    Optional<ChatMessageTranslation> findByIdAndDeletedAtIsNull(Long id);


    Optional<ChatMessageTranslation> findByChatMessageIdAndLanguageCodeAndDeletedAtIsNull(
            Long chatMessageId,
            String languageCode
    );


    List<ChatMessageTranslation> findByChatMessageIdInAndDeletedAtIsNull(
            Collection<Long> chatMessageIds
    );


    List<ChatMessageTranslation> findByStatusAndDeletedAtIsNullOrderByIdAsc(
            ChatMessageTranslationStatus status,
            Pageable pageable
    );

    List<ChatMessageTranslation> findByIdInAndStatusAndDeletedAtIsNull(
            List<Long> ids,
            ChatMessageTranslationStatus status
    );

}
