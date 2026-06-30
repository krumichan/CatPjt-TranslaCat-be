package jp.co.translacat.domain.chat.translation.service;

import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageTranslationResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationRequestedEvent;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.utils.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageTranslationRetryService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageTranslationRepository chatMessageTranslationRepository;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ChatMessageTranslationResponseDto retry(
            Long loginUserId,
            Long chatRoomId,
            Long messageId,
            String languageCode
    ) {
        validateRequest(chatRoomId, messageId, languageCode);

        chatRoomMemberQueryService.getActiveMember(loginUserId, chatRoomId);

        ChatMessage message = chatMessageRepository
                .findByIdAndChatRoomIdAndDeletedAtIsNull(messageId, chatRoomId)
                .orElseThrow(() -> new BusinessException("채팅 메시지를 찾을 수 없습니다."));

        ChatMessageTranslation translation = chatMessageTranslationRepository
                .findByChatMessageIdAndLanguageCodeAndDeletedAtIsNull(
                        message.getId(),
                        normalizeLanguageCode(languageCode)
                )
                .orElseThrow(() -> new BusinessException("재시도할 번역 정보를 찾을 수 없습니다."));

        /*
         * 자동 재처리 배치와 경합할 수 있으므로 PENDING/COMPLETED 상태는 에러로 보지 않는다.
         *
         * - FAILED: 사용자가 직접 재시도 요청 → PENDING 전환 후 이벤트 발행
         * - PENDING: 이미 자동 배치 또는 이전 수동 요청으로 재처리 중 → 현재 상태 반환
         * - COMPLETED: 자동 배치가 먼저 완료했을 수 있음 → 완료 상태 반환
         */
        if (translation.isFailed()) {
            translation.retry();

            applicationEventPublisher.publishEvent(
                    ChatMessageTranslationRequestedEvent.of(
                            chatRoomId,
                            message.getId(),
                            message.getSenderUser() != null
                                    ? message.getSenderUser().getId()
                                    : null,
                            List.of(translation.getId())
                    )
            );
        }

        return ChatMessageTranslationResponseDto.from(translation);
    }

    private void validateRequest(
            Long chatRoomId,
            Long messageId,
            String languageCode
    ) {
        if (chatRoomId == null || chatRoomId <= 0) {
            throw new BusinessException("chatRoomId는 1 이상이어야 합니다.");
        }

        if (messageId == null || messageId <= 0) {
            throw new BusinessException("messageId는 1 이상이어야 합니다.");
        }

        if (ValidationUtil.isBlank(languageCode)) {
            throw new BusinessException("languageCode는 필수입니다.");
        }
    }

    private String normalizeLanguageCode(String languageCode) {
        return languageCode.trim().toLowerCase();
    }
}
