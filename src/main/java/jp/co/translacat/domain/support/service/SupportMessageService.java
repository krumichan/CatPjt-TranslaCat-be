package jp.co.translacat.domain.support.service;

import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.entity.SupportMessage;
import jp.co.translacat.domain.support.enums.*;
import jp.co.translacat.domain.support.exception.SupportException;
import jp.co.translacat.domain.support.repository.SupportMessageRepository;
import jp.co.translacat.domain.support.repository.SupportRecipientRepository;
import jp.co.translacat.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportMessageService {

    private final SupportMessageRepository messageRepository;
    private final SupportRecipientRepository recipientRepository;
    private final SupportAccessService accessService;
    private final SupportNotificationService notificationService;

    public SupportPageResponseDto<MessageResponseDto> list(Long actorId, int page, int size) {
        User actor = accessService.user(actorId);
        return SupportPageResponseDto.from(
                messageRepository.search(
                        accessService.isAdmin(actor), actorId,
                        accessService.page(page, size, Sort.by("id").descending())
                ),
                MessageResponseDto::from
        );
    }

    @Transactional
    public MessageResponseDto detail(Long actorId, Long messageId) {
        // 관리자의 발송 확인은 수신자의 읽음 상태를 바꾸지 않는다.
        User actor = accessService.user(actorId);
        SupportMessage message = messageRepository.findLockedById(messageId)
                .orElseThrow(SupportException::notFound);
        accessService.requireOwnerOrAdmin(actor, message.getRecipient().getId());

        if (message.getRecipient().getId().equals(actorId)) {
            message.markRead();
            notificationService.markTargetRead(actorId, SupportTargetType.MESSAGE, messageId);
        }

        return MessageResponseDto.from(message);
    }

    @Transactional
    public MessageResponseDto create(Long actorId, MessageCreateRequestDto request) {
        // 발신자는 인증된 관리자이며 수신자는 현재 존재하는 계정이어야 한다.
        User sender = accessService.admin(actorId);
        User recipient = accessService.user(request.recipientId());
        SupportMessage message = messageRepository.saveAndFlush(
                SupportMessage.create(
                        sender, recipient, request.title().strip(), request.content().strip()
                )
        );

        notificationService.notifyUser(
                recipient, actorId, SupportNotificationType.SUPPORT_MESSAGE,
                SupportTargetType.MESSAGE, message.getId(), null, message.getTitle()
        );
        return MessageResponseDto.from(message);
    }

    public List<SupportUserResponseDto> recipients(Long actorId, String query) {
        // 사용자 검색과 이메일 표시는 관리자 전용으로 제한한다.
        accessService.admin(actorId);
        String pattern = accessService.searchPattern(query);
        if (query == null || query.strip().length() < 2) {
            return List.of();
        }

        return recipientRepository.search(pattern, PageRequest.of(0, 20))
                .stream().map(SupportUserResponseDto::from).toList();
    }
}
