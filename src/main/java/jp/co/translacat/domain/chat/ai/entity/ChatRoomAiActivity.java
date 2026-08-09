package jp.co.translacat.domain.chat.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_room_ai_activity",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_room_ai_activity_room",
                        columnNames = "chat_room_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_room_ai_activity_revival_due",
                        columnList = "revival_stopped, next_revival_at"
                ),
                @Index(
                        name = "idx_chat_room_ai_activity_claim",
                        columnList = "claim_expires_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomAiActivity extends BaseAuditable {

    public static final int MAX_REVIVAL_STAGE = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false, updatable = false)
    private ChatRoom chatRoom;

    @Column(name = "last_human_message_id")
    private Long lastHumanMessageId;

    @Column(name = "last_human_message_at")
    private LocalDateTime lastHumanMessageAt;

    @Column(name = "revival_cycle_version", nullable = false)
    private long revivalCycleVersion;

    @Column(name = "revival_stage", nullable = false)
    private int revivalStage;

    @Column(name = "last_revival_at")
    private LocalDateTime lastRevivalAt;

    @Column(name = "next_revival_at")
    private LocalDateTime nextRevivalAt;

    @Column(name = "revival_stopped", nullable = false)
    private boolean revivalStopped;

    @Column(name = "last_revival_ai_member_id")
    private Long lastRevivalAiMemberId;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "claim_expires_at")
    private LocalDateTime claimExpiresAt;

    private ChatRoomAiActivity(
            ChatRoom chatRoom,
            Long humanMessageId,
            LocalDateTime humanMessageAt,
            LocalDateTime nextRevivalAt
    ) {
        if (chatRoom == null || humanMessageAt == null) {
            throw new IllegalArgumentException(
                    "채팅방과 마지막 사람 메시지 시각은 필수입니다."
            );
        }
        this.chatRoom = chatRoom;
        this.revivalCycleVersion = 1L;
        applyHumanMessage(
                humanMessageId,
                humanMessageAt,
                nextRevivalAt,
                false
        );
    }

    public static ChatRoomAiActivity create(
            ChatRoom chatRoom,
            Long humanMessageId,
            LocalDateTime humanMessageAt,
            LocalDateTime nextRevivalAt
    ) {
        return new ChatRoomAiActivity(
                chatRoom,
                humanMessageId,
                humanMessageAt,
                nextRevivalAt
        );
    }

    public boolean resetForHumanMessage(
            Long humanMessageId,
            LocalDateTime humanMessageAt,
            LocalDateTime nextRevivalAt
    ) {
        if (!isNewerHumanMessage(humanMessageId, humanMessageAt)) {
            return false;
        }
        this.revivalCycleVersion++;
        applyHumanMessage(
                humanMessageId,
                humanMessageAt,
                nextRevivalAt,
                true
        );
        return true;
    }

    public boolean tryClaim(
            String token,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        if (token == null || token.isBlank()
                || now == null || expiresAt == null
                || !expiresAt.isAfter(now)) {
            return false;
        }
        if (revivalStopped || nextRevivalAt == null
                || nextRevivalAt.isAfter(now)) {
            return false;
        }
        if (claimExpiresAt != null && claimExpiresAt.isAfter(now)) {
            return false;
        }
        this.claimToken = token;
        this.claimExpiresAt = expiresAt;
        return true;
    }

    public boolean completeClaimedAttempt(
            String token,
            long cycleVersion,
            int attemptNumber,
            Long aiMemberId,
            LocalDateTime attemptedAt,
            LocalDateTime nextRevivalAt
    ) {
        if (!matchesClaim(token, cycleVersion, attemptNumber)) {
            return false;
        }

        this.revivalStage = attemptNumber;
        this.lastRevivalAt = attemptedAt;
        this.lastRevivalAiMemberId = aiMemberId;
        clearClaim();

        if (attemptNumber >= MAX_REVIVAL_STAGE) {
            this.revivalStopped = true;
            this.nextRevivalAt = null;
        } else {
            this.revivalStopped = false;
            this.nextRevivalAt = nextRevivalAt;
        }
        return true;
    }

    public boolean retryClaimedAttempt(
            String token,
            long cycleVersion,
            int attemptNumber,
            LocalDateTime retryAt
    ) {
        if (!matchesClaim(token, cycleVersion, attemptNumber)) {
            return false;
        }
        this.nextRevivalAt = retryAt;
        clearClaim();
        return true;
    }

    public boolean releaseClaim(
            String token,
            long cycleVersion,
            int attemptNumber
    ) {
        if (!matchesClaim(token, cycleVersion, attemptNumber)) {
            return false;
        }
        clearClaim();
        return true;
    }

    public void clearExpiredClaim(LocalDateTime now) {
        if (claimToken == null || now == null) {
            return;
        }
        if (claimExpiresAt == null || !claimExpiresAt.isAfter(now)) {
            clearClaim();
        }
    }

    public boolean postponeDue(LocalDateTime postponedAt) {
        if (revivalStopped
                || postponedAt == null
                || claimToken != null) {
            return false;
        }
        this.nextRevivalAt = postponedAt;
        return true;
    }

    public boolean isDue(LocalDateTime now) {
        return !revivalStopped
                && nextRevivalAt != null
                && now != null
                && !nextRevivalAt.isAfter(now);
    }

    public boolean hasActiveClaim(LocalDateTime now) {
        return claimToken != null
                && claimExpiresAt != null
                && now != null
                && claimExpiresAt.isAfter(now);
    }

    private boolean matchesClaim(
            String token,
            long cycleVersion,
            int attemptNumber
    ) {
        return token != null
                && token.equals(this.claimToken)
                && cycleVersion == this.revivalCycleVersion
                && attemptNumber == this.revivalStage + 1;
    }

    private boolean isNewerHumanMessage(
            Long humanMessageId,
            LocalDateTime humanMessageAt
    ) {
        if (humanMessageAt == null) {
            return false;
        }
        if (lastHumanMessageId != null && humanMessageId != null) {
            return humanMessageId > lastHumanMessageId;
        }
        return lastHumanMessageAt == null
                || humanMessageAt.isAfter(lastHumanMessageAt);
    }

    private void applyHumanMessage(
            Long humanMessageId,
            LocalDateTime humanMessageAt,
            LocalDateTime nextRevivalAt,
            boolean preserveLastAiMember
    ) {
        this.lastHumanMessageId = humanMessageId;
        this.lastHumanMessageAt = humanMessageAt;
        this.revivalStage = 0;
        this.lastRevivalAt = null;
        this.nextRevivalAt = nextRevivalAt;
        this.revivalStopped = false;
        if (!preserveLastAiMember) {
            this.lastRevivalAiMemberId = null;
        }
        clearClaim();
    }

    private void clearClaim() {
        this.claimToken = null;
        this.claimExpiresAt = null;
    }
}
