package jp.co.translacat.domain.chat.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Serializes the chat domain's zone-less LocalDateTime values as UTC instants.
 *
 * <p>The current persistence model uses LocalDateTime, so the stored value is
 * interpreted in the JVM's source time zone and converted to an absolute UTC
 * instant for the external JSON contract. Production explicitly runs with
 * UTC as its JVM time zone, while local development keeps working correctly
 * when the JVM uses a local zone such as Asia/Tokyo.</p>
 */
public class ChatUtcLocalDateTimeSerializer
        extends JsonSerializer<LocalDateTime> {

    @Override
    public void serialize(
            LocalDateTime value,
            JsonGenerator generator,
            SerializerProvider serializers
    ) throws IOException {
        generator.writeString(
                value.atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toString()
        );
    }
}
