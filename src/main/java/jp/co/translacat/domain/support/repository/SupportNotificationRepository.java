package jp.co.translacat.domain.support.repository;

import jp.co.translacat.domain.support.entity.SupportNotification;
import jp.co.translacat.domain.support.enums.SupportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SupportNotificationRepository extends JpaRepository<SupportNotification, Long> {

    @Modifying
    @Query(value = """
            insert into support_notification
                (recipient_id, type, target_type, target_id, title, created_at, created_by)
            select u.id, 'SUPPORT_NOTICE', 'NOTICE', :noticeId, :title, :createdAt, :createdBy
            from `user` u where u.id <> :authorId
            """, nativeQuery = true)
    int broadcastNotice(
            @Param("noticeId") Long noticeId,
            @Param("title") String title,
            @Param("authorId") Long authorId,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("createdBy") String createdBy
    );

    @Query("""
            select n from SupportNotification n
            where n.recipient.id = :userId and (:unreadOnly = false or n.readAt is null)
              and (n.type <> jp.co.translacat.domain.support.enums.SupportNotificationType.SUPPORT_INQUIRY
                   or n.recipient.authority = jp.co.translacat.domain.user.enums.Role.ADMIN)
            """)
    Page<SupportNotification> search(
            @Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly, Pageable pageable
    );

    @Query("""
            select count(n) from SupportNotification n
            where n.recipient.id = :userId and n.readAt is null
              and (n.type <> jp.co.translacat.domain.support.enums.SupportNotificationType.SUPPORT_INQUIRY
                   or n.recipient.authority = jp.co.translacat.domain.user.enums.Role.ADMIN)
            """)
    long countByRecipient_IdAndReadAtIsNull(@Param("userId") Long userId);

    @Query("""
            select distinct n.targetId from SupportNotification n
            where n.recipient.id = :userId and n.targetType = :targetType
              and n.targetId in :targetIds and n.readAt is null
            """)
    List<Long> findUnreadTargetIds(
            @Param("userId") Long userId,
            @Param("targetType") SupportTargetType targetType,
            @Param("targetIds") Collection<Long> targetIds
    );

    @Modifying
    @Query("""
            update SupportNotification n set n.readAt = :readAt
            where n.recipient.id = :userId and n.targetType = :targetType
              and n.targetId = :targetId and n.readAt is null
            """)
    int markTargetRead(
            @Param("userId") Long userId,
            @Param("targetType") SupportTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("readAt") LocalDateTime readAt
    );

    @Query("""
            select count(n) > 0 from SupportNotification n
            where n.id = :id and n.recipient.id = :userId
              and (n.type <> jp.co.translacat.domain.support.enums.SupportNotificationType.SUPPORT_INQUIRY
                   or n.recipient.authority = jp.co.translacat.domain.user.enums.Role.ADMIN)
            """)
    boolean existsByIdAndRecipient_Id(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying
    @Query("""
            update SupportNotification n set n.readAt = :readAt
            where n.recipient.id = :userId and n.id = :id and n.readAt is null
            """)
    int markRead(
            @Param("userId") Long userId, @Param("id") Long id, @Param("readAt") LocalDateTime readAt
    );

    @Modifying
    @Query("""
            update SupportNotification n set n.readAt = :readAt
            where n.recipient.id = :userId and n.readAt is null
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("delete from SupportNotification n where n.targetType = :targetType and n.targetId = :targetId")
    int deleteByTarget(@Param("targetType") SupportTargetType targetType, @Param("targetId") Long targetId);
}
