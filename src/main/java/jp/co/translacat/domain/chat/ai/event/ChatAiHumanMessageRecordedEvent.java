package jp.co.translacat.domain.chat.ai.event;

import java.time.LocalDateTime;

public record ChatAiHumanMessageRecordedEvent(
        Long messageId,
        Long roomId,
        LocalDateTime createdAt
) {
}
