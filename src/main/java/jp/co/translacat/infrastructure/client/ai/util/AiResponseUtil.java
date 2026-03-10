package jp.co.translacat.infrastructure.client.ai.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
@UtilityClass
public class AiResponseUtil {

    /**
     * AI 응답에서 마크다운 태그를 제거하고 순수 JSON 배열 부분만 추출합니다.
     */
    public static String extractJsonArray(String response) {
        if (!StringUtils.hasText(response)) {
            return "[]";
        }

        // 1. 마크다운 제거
        String cleaned = response.replaceAll("(?s)```(?:json)?\\s*|```", "").trim();

        // 2. JSON 배열 시작과 끝 추출
        int start = cleaned.indexOf("[");
        int end = cleaned.lastIndexOf("]");

        if (start != -1 && end != -1 && start < end) {
            return cleaned.substring(start, end + 1);
        }

        log.warn("JSON 배열 형식을 찾을 수 없습니다. 원문 일부: {}",
                cleaned.substring(0, Math.min(cleaned.length(), 50)));

        return cleaned;
    }
}
