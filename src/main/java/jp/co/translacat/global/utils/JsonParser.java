package jp.co.translacat.global.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonParser {
    private final ObjectMapper objectMapper;

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON conversion failed", e);
            throw new RuntimeException("JSON 변환 실패", e);
        }
    }

    @SneakyThrows
    public <T> List<T> parseToList(String raw, Class<T> clazz) {
        // TODO: 로그 남길지 말지 결정 필요.
//        log.info("JsonParser - class: {}, parse target: {}", clazz.getName(), raw);
        try {
            return objectMapper.readValue(raw,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            log.error("JSON conversion failed", e);
            throw new RuntimeException("JSON conversion failed", e);
        }

    }

    public Object parseToObject(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            log.error("JSON to Object parsing failed", e);
            throw new RuntimeException("JSON to Object parsing failed", e);
        }
    }
}
