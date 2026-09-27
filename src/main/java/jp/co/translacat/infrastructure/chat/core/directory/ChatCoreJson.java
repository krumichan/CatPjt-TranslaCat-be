package jp.co.translacat.infrastructure.chat.core.directory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Map;

public final class ChatCoreJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private ChatCoreJson() { }

    public static JsonNode read(HttpServletRequest request, int limit) throws IOException {
        byte[] bytes = request.getInputStream().readNBytes(limit + 1);
        if (bytes.length == 0 || bytes.length > limit) throw new IOException();
        JsonNode body = MAPPER.readTree(bytes);
        if (body == null || !body.isObject()) throw new IOException();
        return body;
    }

    public static ResponseEntity<?> rejected(int status) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(Map.of("code", "CHAT_CORE_REQUEST_REJECTED"));
    }
}
