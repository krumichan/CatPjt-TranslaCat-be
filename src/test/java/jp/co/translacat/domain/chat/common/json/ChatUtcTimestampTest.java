package jp.co.translacat.domain.chat.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.enums.ChatMessageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatUtcTimestampTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesChatLocalDateTimeAsUtcInstantWithZoneInformation()
            throws Exception {
        LocalDateTime localDateTime = testDateTime();
        String expectedUtc = expectedUtc(localDateTime);

        String json = objectMapper.writeValueAsString(
                new TimestampFixture(localDateTime)
        );

        assertThat(json)
                .contains("\"occurredAt\":\"" + expectedUtc + "\"");
        assertThat(expectedUtc).endsWith("Z");
    }

    @Test
    void chatMessageResponseExposesCreatedAndUpdatedAtAsUtcInstants()
            throws Exception {
        LocalDateTime localDateTime = testDateTime();
        String expectedUtc = expectedUtc(localDateTime);
        ChatMessageResponseDto response = new ChatMessageResponseDto(
                100L,
                10L,
                1L,
                null,
                "sender",
                "sender@example.com",
                null,
                ChatMessageSenderType.USER,
                ChatMessageType.TEXT,
                "hello",
                ChatMessageStatus.SENT,
                0L,
                List.of(),
                localDateTime,
                localDateTime,
                null
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\"createdAt\":\"" + expectedUtc + "\"")
                .contains("\"updatedAt\":\"" + expectedUtc + "\"");
    }

    private LocalDateTime testDateTime() {
        return LocalDateTime.of(
                2026,
                8,
                11,
                20,
                1,
                2,
                345_000_000
        );
    }

    private String expectedUtc(LocalDateTime localDateTime) {
        return localDateTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toString();
    }

    private record TimestampFixture(
            @ChatUtcTimestamp LocalDateTime occurredAt
    ) {
    }
}
