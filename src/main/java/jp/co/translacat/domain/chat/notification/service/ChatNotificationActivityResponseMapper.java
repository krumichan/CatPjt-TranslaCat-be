package jp.co.translacat.domain.chat.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityItemResponseDto;
import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatNotificationActivityResponseMapper {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public ChatNotificationActivityItemResponseDto toResponse(
            ChatNotification notification
    ) {
        Long roomId = notification.getChatRoom() != null
                ? notification.getChatRoom().getId()
                : null;

        return new ChatNotificationActivityItemResponseDto(
                notification.getId(),
                notification.getNotificationType(),
                roomId,
                parsePayload(notification),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private Map<String, Object> parsePayload(
            ChatNotification notification
    ) {
        String payloadJson = notification.getPayloadJson();
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    payloadJson,
                    PAYLOAD_TYPE
            );
            return payload != null ? payload : Map.of();
        } catch (Exception exception) {
            log.warn(
                    "Failed to parse chat notification payload. notificationId={}",
                    notification.getId(),
                    exception
            );
            return Map.of();
        }
    }
}
