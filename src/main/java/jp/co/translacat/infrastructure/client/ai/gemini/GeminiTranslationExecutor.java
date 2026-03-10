package jp.co.translacat.infrastructure.client.ai.gemini;

import jp.co.translacat.domain.novel.translation.model.Translatable;
import jp.co.translacat.global.utils.JsonParser;
import jp.co.translacat.infrastructure.client.ai.util.AiResponseUtil;
import jp.co.translacat.infrastructure.scraping.syosetu.constant.AiGeminiConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiTranslationExecutor {

    private final AiGeminiClient aiGeminiClient;
    private final JsonParser jsonParser;

    private final Semaphore geminiSemaphore = new Semaphore(10);

    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(
            delay = 100,
            multiplier = 1.5,
            maxDelay = 500
        ),
        recover = "recoverTranslation"
    )
    public <T extends Translatable> List<T> executeTranslation(List<T> batch, String rule) {
        String requestJson = "";
        String responseJson = "";

        try {
            this.semaphoreAcquire();

            // 1. 입력 텍스트 추출
            List<String> japaneseTexts = batch.stream()
                .map(Translatable::getRawJa)
                .toList();

            requestJson = jsonParser.toJson(japaneseTexts);

            // 2. API 호출 및 파싱
            String response = aiGeminiClient.call(rule, requestJson, AiGeminiConstant.STRING_LIST_SCHEMA);
            responseJson = AiResponseUtil.extractJsonArray(response);

            // 3. 결과값을 문자열로 파싱.
            List<String> translatedTexts = jsonParser.parseToList(responseJson, String.class);

            // 4. 무결성 검증.
            if (japaneseTexts.size() != translatedTexts.size()) {
                throw new RuntimeException(String.format(
                        "Translation size mismatch: Expected %d, but received %d",
                        japaneseTexts.size(), translatedTexts.size()
                ));
            }

            // 5. 결과 배분 (인덱스를 활용한 1:1 매핑)
            for (int i = 0; i < batch.size(); i++) {
                T item = batch.get(i);
                String translatedKo = translatedTexts.get(i);
                item.setKo(translatedKo);
            }

            return batch;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Gemini processing interrupted", e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Gemini batch processing failed!");
            log.error("- Request JSON: {}", requestJson);
            log.error("- Response Cleaned: {}", responseJson);
            log.error("- Target Batch: {}", batch, e);
            throw e;
        } finally {
            this.semaphoreRelease();
        }
    }

    @Recover
    public <T extends Translatable> List<T> recoverTranslation(Exception e, List<T> batch, String rule) {
        log.error("Gemini batch processing finally failed after retries!");
        log.error("Reason: {}, Rule: {}, Batch: {}", e.getMessage(), rule, batch);
        return Collections.emptyList();
    }

    private void semaphoreAcquire() throws InterruptedException {
        try {
            geminiSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Gemini processing interrupted", e);
            throw e;
        }
    }

    private void semaphoreRelease() {
        geminiSemaphore.release();
    }
}
