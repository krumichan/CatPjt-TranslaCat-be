package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryItemResponseDto;

import java.io.IOException;

/** 순차 배포 중 이전 LL의 이력에는 새 언어 메타데이터만 null로 보완한다. */
final class HistoryLanguageMetadata {
    private HistoryLanguageMetadata() { }

    static <T> T readValue(ObjectMapper json, byte[] bytes, Class<T> type) throws IOException {
        return readValue(json, bytes, json.constructType(type));
    }

    static <T> T readValue(ObjectMapper json, byte[] bytes, JavaType type) throws IOException {
        // 대상 DTO의 타입을 따라 중첩 이력/레벨 summary까지 확인하며 원문 JSON에는 손대지 않는다.
        JsonNode value = json.readTree(bytes);
        supplement(json, value, type);
        return json.readValue(value.traverse(json), type);
    }

    private static void supplement(ObjectMapper json, JsonNode value, JavaType type) {
        if (value == null || value.isNull()) return;
        if (type.isContainerType() && value.isArray()) {
            value.forEach(item -> supplement(json, item, type.getContentType()));
            return;
        }
        if (!(value instanceof ObjectNode object)) return;

        // 기존 필수 creator 속성은 그대로 엄격 검사한다. 두 이력 DTO의 새 필드만 선택적이다.
        Class<?> raw = type.getRawClass();
        if (raw == LearningHistoryItemResponseDto.class || raw == LevelTestHistoryItemResponseDto.class) {
            if (!object.has("originLanguage")) object.putNull("originLanguage");
            if (!object.has("learningLanguage")) object.putNull("learningLanguage");
        }
        if (raw.isRecord()) {
            for (var component : raw.getRecordComponents()) {
                supplement(json, object.get(component.getName()), json.constructType(component.getGenericType()));
            }
        }
    }
}
