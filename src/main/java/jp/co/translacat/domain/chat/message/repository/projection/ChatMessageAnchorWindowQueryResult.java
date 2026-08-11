package jp.co.translacat.domain.chat.message.repository.projection;

import java.util.List;

public record ChatMessageAnchorWindowQueryResult(
        List<Long> messageIds,
        Long previousCursorId,
        boolean hasPrevious,
        Long nextCursorId,
        boolean hasNext
) {
    public static ChatMessageAnchorWindowQueryResult empty() {
        return new ChatMessageAnchorWindowQueryResult(
                List.of(),
                null,
                false,
                null,
                false
        );
    }
}
