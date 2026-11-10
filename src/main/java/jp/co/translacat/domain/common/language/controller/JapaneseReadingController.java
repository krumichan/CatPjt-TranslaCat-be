package jp.co.translacat.domain.common.language.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class JapaneseReadingController {

    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final int MAX_TEXTS = 12;
    private static final int MAX_TEXT_LENGTH = 3_000;
    private static final int MAX_TOTAL_LENGTH = 8_000;

    private final ObjectMapper mapper;
    private final SudachiFuriganaProcessor sudachi;

    @PostMapping(path = "/api/v1/text-readings/japanese", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDto<List<List<Reading>>>> readings(HttpServletRequest request) throws IOException {
        // 파싱과 분석 전에 실제 전송 크기와 UTF-16 텍스트 예산을 각각 제한한다.
        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        List<String> texts = readTexts(body);
        if (texts.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResponseUtil.ok(texts.stream().map(this::annotate).toList()));
    }

    private List<String> readTexts(byte[] body) throws IOException {
        JsonNode input;
        try {
            input = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
        } catch (JsonProcessingException invalid) {
            return List.of();
        }
        JsonNode values = input == null ? null : input.get("texts");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > MAX_TEXTS) {
            return List.of();
        }

        List<String> texts = new ArrayList<>(values.size());
        int totalLength = 0;
        for (JsonNode value : values) {
            if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > MAX_TEXT_LENGTH) {
                return List.of();
            }
            String text = value.textValue();
            totalLength += text.length();
            if (totalLength > MAX_TOTAL_LENGTH) {
                return List.of();
            }
            texts.add(text);
        }
        return texts;
    }

    private List<Reading> annotate(String text) {
        if (text.codePoints().noneMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)) {
            return List.of();
        }

        // 기존 Sudachi가 반환한 표면형을 원래 번역에 대응시킨다. 원문은 저장하거나 재생성하지 않는다.
        try {
            List<Reading> readings = new ArrayList<>();
            int offset = 0;
            for (var token : sudachi.tokenizeWithReadings(text)) {
                if (!text.startsWith(token.surface(), offset)) {
                    return List.of();
                }
                if (token.reading() != null) {
                    readings.add(new Reading(offset, token.surface(), token.reading()));
                }
                offset += token.surface().length();
            }
            return offset == text.length() ? readings : List.of();
        } catch (RuntimeException error) {
            log.warn("Japanese reading generation failed errorType={}", error.getClass().getSimpleName());
            return List.of();
        }
    }

    public record Reading(int startOffset, String surface, String reading) {
    }
}
