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

/** 확정된 음성 원문의 일본어 읽기 토큰을 만들고 원문과의 일치 여부를 검사한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceSourceReadingService {
    private static final int MAX_READING_TOKENS = 2_000;
    private static final int MAX_SOURCE_CHARACTERS = 3_000;

    private final SudachiFuriganaProcessor sudachi;
    private final ObjectMapper objectMapper;

    /**
     * 음성 확정 이벤트에서 일본어로 판정한 원문의 읽기를 만든다.
     *
     * @param context 수동 원문 언어 설정이 담긴 스트림 상태
     * @param event 감지 언어와 원문 텍스트가 담긴 확정 이벤트
     * @return 일본어 읽기 토큰 또는 읽기를 제공할 수 없는 경우 빈 배열
     */
    public ArrayNode forFinal(VoiceStreamContext context, JsonNode event) {
        if (!isJapaneseFinal(context, event)) {
            return objectMapper.createArrayNode();
        }
        return forLanguage(event.path("sourceText").asText(null), "ja");
    }

    /**
     * 일본어 원문을 그대로 재구성할 수 있는 읽기 토큰만 반환한다.
     *
     * @param sourceText 공백과 기호를 포함한 원문
     * @param sourceLanguage 원문 언어 태그
     * @return 분석이 가능하고 재구성 결과가 원문과 일치하면 토큰 배열, 그 외에는 빈 배열
     */
    public ArrayNode forLanguage(String sourceText, String sourceLanguage) {
        // 일본어 입력과 길이 상한을 먼저 확인해 불필요한 형태소 분석을 피한다.
        ArrayNode empty = objectMapper.createArrayNode();
        if (!"ja".equals(normalizeLanguage(sourceLanguage)) || sourceText == null || sourceText.isBlank()
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
            log.warn("Voice Sudachi reading generation failed errorType={}", error.getClass().getSimpleName());
            return empty;
        }
    }

    /**
     * 새 읽기가 빈 배열일 때 원문과 일치하는 기존 토큰을 재사용한다.
     *
     * @param generatedJson 새로 생성한 읽기 토큰 JSON
     * @param storedJson 이전에 저장한 읽기 토큰 JSON
     * @param sourceText 토큰을 검사할 원문
     * @return 재사용 조건을 충족하면 기존 JSON, 그 외에는 새 JSON
     */
    public String preserveStoredWhenGenerationFails(String generatedJson, String storedJson, String sourceText) {
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

    // 확정 발화의 감지 언어를 우선하고 필요한 경우 수동 설정과 잠금 언어를 조회한다.
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

    // 지역과 대소문자를 제거해 기본 언어 태그로 비교한다.
    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return language.trim().toLowerCase(Locale.ROOT).split("-", 2)[0];
    }
}
