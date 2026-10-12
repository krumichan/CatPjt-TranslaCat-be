package jp.co.translacat.domain.support.service;

import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.entity.SupportNotification;
import jp.co.translacat.domain.support.enums.*;
import jp.co.translacat.domain.support.exception.SupportException;
import jp.co.translacat.domain.support.repository.SupportNotificationRepository;
import jp.co.translacat.domain.support.repository.SupportRecipientRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportNotificationService {

    private final SupportNotificationRepository notificationRepository;
    private final SupportRecipientRepository recipientRepository;
    private final SupportAccessService accessService;

    public SupportPageResponseDto<NotificationResponseDto> list(
            Long actorId, boolean unreadOnly, int page, int size
    ) {
        return SupportPageResponseDto.from(
                notificationRepository.search(
                        actorId, unreadOnly, accessService.page(page, size, Sort.by("id").descending())
                ),
                NotificationResponseDto::from
        );
    }

    public NotificationCountResponseDto unreadCount(Long actorId) {
        return new NotificationCountResponseDto(notificationRepository.countByRecipient_IdAndReadAtIsNull(actorId));
    }

    public Set<Long> unreadTargets(Long actorId, SupportTargetType type, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(notificationRepository.findUnreadTargetIds(actorId, type, ids));
    }

    @Transactional
    public boolean markRead(Long actorId, Long notificationId) {
        // 이미 읽은 알림은 성공으로 처리하되 타인의 알림은 변경하지 않는다.
        if (!notificationRepository.existsByIdAndRecipient_Id(notificationId, actorId)) {
            throw SupportException.notFound();
        }

        notificationRepository.markRead(actorId, notificationId, LocalDateTime.now());
        return true;
    }

    @Transactional
    public boolean markAllRead(Long actorId) {
        notificationRepository.markAllRead(actorId, LocalDateTime.now());
        return true;
    }

    @Transactional
    public void markTargetRead(Long actorId, SupportTargetType type, Long targetId) {
        notificationRepository.markTargetRead(actorId, type, targetId, LocalDateTime.now());
    }

    @Transactional
    public void notifyUser(
            User recipient, Long actorId, SupportNotificationType type, SupportTargetType targetType,
            Long targetId, Long replyId, String title
    ) {
        if (recipient.getId().equals(actorId)) {
            return;
        }

        notificationRepository.save(
                SupportNotification.create(recipient, type, targetType, targetId, replyId, title)
        );
    }

    @Transactional
    public void notifyAdmins(Long actorId, Long inquiryId, Long replyId, String title) {
        // 문의 접수와 추가 질문은 현재 ADMIN인 운영자에게만 전달한다.
        for (User admin : recipientRepository.findByAuthority(Role.ADMIN)) {
            notifyUser(
                    admin, actorId, SupportNotificationType.SUPPORT_INQUIRY,
                    SupportTargetType.INQUIRY, inquiryId, replyId, title
            );
        }
    }

    @Transactional
    public void broadcastNotice(Long actorId, Long noticeId, String title) {
        // 전체 사용자 엔티티를 메모리에 올리지 않고 공지 저장과 같은 트랜잭션에서 생성한다.
        notificationRepository.broadcastNotice(
                noticeId, title, actorId, LocalDateTime.now(), actorId.toString()
        );
    }

    @Transactional
    public void deleteTarget(SupportTargetType type, Long targetId) {
        notificationRepository.deleteByTarget(type, targetId);
    }
}
