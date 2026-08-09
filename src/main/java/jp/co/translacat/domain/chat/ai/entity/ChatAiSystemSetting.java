package jp.co.translacat.domain.chat.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Getter
@Table(name = "chat_ai_system_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatAiSystemSetting extends BaseAuditable {

    public static final String DEFAULT_ID = "DEFAULT";

    @Id
    @Column(length = 30)
    private String id;

    @Column(name = "max_ai_members_per_room", nullable = false)
    private int maxAiMembersPerRoom;

    @Column(name = "conversation_response_rate", nullable = false)
    private int conversationResponseRate;

    @Column(name = "conversation_cooldown_seconds", nullable = false)
    private int conversationCooldownSeconds;

    @Column(name = "conversation_min_human_messages_after_ai", nullable = false)
    private int conversationMinHumanMessagesAfterAi;

    @Column(name = "revival_first_delay_hours", nullable = false)
    private int revivalFirstDelayHours;

    @Column(name = "revival_second_delay_hours", nullable = false)
    private int revivalSecondDelayHours;

    @Column(name = "revival_third_delay_hours", nullable = false)
    private int revivalThirdDelayHours;

    @Column(name = "revival_allowed_start_time", nullable = false)
    private LocalTime revivalAllowedStartTime;

    @Column(name = "revival_allowed_end_time", nullable = false)
    private LocalTime revivalAllowedEndTime;

    @Column(name = "context_max_messages", nullable = false)
    private int contextMaxMessages;

    @Column(name = "context_max_characters", nullable = false)
    private int contextMaxCharacters;

    @Column(name = "reply_max_characters", nullable = false)
    private int replyMaxCharacters;

    @Column(name = "mention_rate_limit_count", nullable = false)
    private int mentionRateLimitCount;

    @Column(name = "mention_rate_limit_window_seconds", nullable = false)
    private int mentionRateLimitWindowSeconds;

    private ChatAiSystemSetting(String id) {
        this.id = id;
        this.maxAiMembersPerRoom = 2;
        this.conversationResponseRate = 15;
        this.conversationCooldownSeconds = 180;
        this.conversationMinHumanMessagesAfterAi = 2;
        this.revivalFirstDelayHours = 24;
        this.revivalSecondDelayHours = 72;
        this.revivalThirdDelayHours = 168;
        this.revivalAllowedStartTime = LocalTime.of(10, 0);
        this.revivalAllowedEndTime = LocalTime.of(22, 0);
        this.contextMaxMessages = 30;
        this.contextMaxCharacters = 12_000;
        this.replyMaxCharacters = 800;
        this.mentionRateLimitCount = 5;
        this.mentionRateLimitWindowSeconds = 60;
    }

    public static ChatAiSystemSetting createDefault() {
        return new ChatAiSystemSetting(DEFAULT_ID);
    }

    public void update(
            Integer maxAiMembersPerRoom,
            Integer conversationResponseRate,
            Integer conversationCooldownSeconds,
            Integer conversationMinHumanMessagesAfterAi,
            Integer revivalFirstDelayHours,
            Integer revivalSecondDelayHours,
            Integer revivalThirdDelayHours,
            LocalTime revivalAllowedStartTime,
            LocalTime revivalAllowedEndTime,
            Integer contextMaxMessages,
            Integer contextMaxCharacters,
            Integer replyMaxCharacters,
            Integer mentionRateLimitCount,
            Integer mentionRateLimitWindowSeconds
    ) {
        if (maxAiMembersPerRoom != null) this.maxAiMembersPerRoom = maxAiMembersPerRoom;
        if (conversationResponseRate != null) this.conversationResponseRate = conversationResponseRate;
        if (conversationCooldownSeconds != null) this.conversationCooldownSeconds = conversationCooldownSeconds;
        if (conversationMinHumanMessagesAfterAi != null) this.conversationMinHumanMessagesAfterAi = conversationMinHumanMessagesAfterAi;
        if (revivalFirstDelayHours != null) this.revivalFirstDelayHours = revivalFirstDelayHours;
        if (revivalSecondDelayHours != null) this.revivalSecondDelayHours = revivalSecondDelayHours;
        if (revivalThirdDelayHours != null) this.revivalThirdDelayHours = revivalThirdDelayHours;
        if (revivalAllowedStartTime != null) this.revivalAllowedStartTime = revivalAllowedStartTime;
        if (revivalAllowedEndTime != null) this.revivalAllowedEndTime = revivalAllowedEndTime;
        if (contextMaxMessages != null) this.contextMaxMessages = contextMaxMessages;
        if (contextMaxCharacters != null) this.contextMaxCharacters = contextMaxCharacters;
        if (replyMaxCharacters != null) this.replyMaxCharacters = replyMaxCharacters;
        if (mentionRateLimitCount != null) this.mentionRateLimitCount = mentionRateLimitCount;
        if (mentionRateLimitWindowSeconds != null) this.mentionRateLimitWindowSeconds = mentionRateLimitWindowSeconds;
    }
}
