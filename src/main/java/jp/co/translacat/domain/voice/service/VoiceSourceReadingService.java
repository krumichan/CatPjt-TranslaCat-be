package jp.co.translacat.domain.voice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor.ReadingToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceSourceReadingService {

    private static final int MAX_READING_TOKENS = 2_000;
    private static final int MAX_SOURCE_CHARACTERS = 3_000;

    private final SudachiFuriganaProcessor sudachi;
    private final ObjectMapper objectMapper;

    public ArrayNode forFinal(VoiceStreamContext context, JsonNode event) {
        if (!isJapaneseFinal(context, event)) {
            return objectMapper.createArrayNode();
        }
        return forLanguage(event.path("sourceText").asText(null), "ja");
    }

    public ArrayNode forLanguage(String sourceText, String sourceLanguage) {
        ArrayNode empty = objectMapper.createArrayNode();
        if (!"ja".equals(normalizeLanguage(sourceLanguage))
                || sourceText == null
                || sourceText.isBlank()
                || sourceText.length() > MAX_SOURCE_CHARACTERS) {
            return empty;
        }

        try {
            // 번역 호출과 독립적인 로컬 형태소 분석이다. 분석 실패는 읽기만 생략하고 원문과 번역을 유지한다.
            List<ReadingToken> tokens = sudachi.tokenizeWithReadings(sourceText);
            if (tokens.isEmpty() || tokens.size() > MAX_READING_TOKENS) {
                return empty;
            }

            // 화면이 토큰 목록으로 원문 전체를 렌더링하므로 공백·기호를 포함한 완전한 재구성을 확인한다.
            StringBuilder reconstructed = new StringBuilder(sourceText.length());
            ArrayNode result = objectMapper.createArrayNode();
            for (ReadingToken token : tokens) {
                if (token == null || token.surface() == null || token.surface().isEmpty()) {
                    return empty;
                }
                reconstructed.append(token.surface());
                ObjectNode jsonToken = result.addObject();
                jsonToken.put("surface", token.surface());
                if (token.reading() == null || token.reading().isBlank()) {
                    jsonToken.putNull("reading");
                } else {
                    jsonToken.put("reading", token.reading());
                }
            }
            return sourceText.contentEquals(reconstructed) ? result : empty;
        } catch (RuntimeException error) {
            log.warn("Voice Sudachi reading generation failed errorType={}",
                    error.getClass().getSimpleName());
            return empty;
        }
    }

    public String preserveStoredWhenGenerationFails(
            String generatedJson,
            String storedJson,
            String sourceText
    ) {
        if (!"[]".equals(generatedJson) || storedJson == null || sourceText == null) {
            return generatedJson;
        }

        try {
            // 과거 AI 토큰도 원문을 완전히 재구성할 때만 재사용한다.
            JsonNode stored = objectMapper.readTree(storedJson);
            if (!stored.isArray() || stored.isEmpty() || stored.size() > MAX_READING_TOKENS) {
                return generatedJson;
            }
            StringBuilder reconstructed = new StringBuilder(sourceText.length());
            for (JsonNode token : stored) {
                JsonNode surface = token.path("surface");
                JsonNode reading = token.path("reading");
                if (!surface.isTextual() || surface.asText().isEmpty()
                        || (!reading.isMissingNode() && !reading.isNull() && !reading.isTextual())) {
                    return generatedJson;
                }
                reconstructed.append(surface.asText());
            }
            return sourceText.contentEquals(reconstructed) ? storedJson : generatedJson;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return generatedJson;
        }
    }

    private boolean isJapaneseFinal(VoiceStreamContext context, JsonNode event) {
        String detected = normalizeLanguage(event.path("detectedLanguage").asText(null));
        JsonNode confidenceNode = event.path("languageConfidence");
        Double confidence = confidenceNode.isNumber() ? confidenceNode.asDouble() : null;

        // 해당 발화에서 신뢰할 만한 감지가 있으면 과거 채널 잠금보다 우선한다.
        if (detected != null) {
            if (!"ja".equals(detected)) {
                return false;
            }
            if (confidence == null || (Double.isFinite(confidence) && confidence >= 0.5)) {
                return true;
            }
        }

        // 낮은 신뢰도에서는 수동 선택 또는 채널의 대표 언어를 사용한다.
        String manual = normalizeLanguage(context.manualSourceLanguage());
        String locked = normalizeLanguage(event.path("lockedLanguage").asText(null));
        return "ja".equals(manual != null ? manual : locked);
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return language.trim().toLowerCase(Locale.ROOT).split("-", 2)[0];
    }
}
