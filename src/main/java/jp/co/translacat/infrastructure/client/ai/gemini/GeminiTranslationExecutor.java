package jp.co.translacat.infrastructure.client.ai.gemini;

import jp.co.translacat.domain.novel.translation.model.Translatable;
import jp.co.translacat.global.utils.BatchProcessor;
import jp.co.translacat.global.utils.JsonParser;
import jp.co.translacat.infrastructure.client.ai.common.AbstractTranslationExecutor;
import jp.co.translacat.infrastructure.client.ai.common.TranslationType;
import jp.co.translacat.infrastructure.client.ai.server.AiRuleType;
import jp.co.translacat.infrastructure.client.ai.util.AiResponseUtil;
import jp.co.translacat.infrastructure.scraping.syosetu.constant.AiGeminiConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiTranslationExecutor extends AbstractTranslationExecutor {

    private final AiGeminiClient aiGeminiClient;
    private final JsonParser jsonParser;
    private final BatchProcessor batchProcessor;

    private final Semaphore geminiSemaphore = new Semaphore(10);

    @Override
    @Retryable(
            retryFor = { Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 500),
            recover = "recoverTranslation"
    )
    public <T extends Translatable> List<T> executeTranslation(List<T> sourceList, AiRuleType rule) {
        return this.executeTranslation(sourceList, rule, null);
    }

    @Override
    public <T extends Translatable> List<T> executeTranslation(
            List<T> sourceList,
            AiRuleType rule,
            Comparator<T> comparator
    ) {
        log.info("[Gemini-Direct] Internal parallel processing with sorting. Size: {}", sourceList.size());
        AiGeminiConstant.InternalInfo info = AiGeminiConstant.internalMap.get(rule);
        return batchProcessor.processParallel(
                sourceList,
                info.batchSize(),
                batch -> {
                    try {
                        return super.executeTranslation(batch, rule);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                comparator
        );
    }

    @Override
    protected List<String> doTranslate(List<String> texts, AiRuleType rule) {
        String requestJson = "";
        String responseJson = "";

        try {
            this.semaphoreAcquire();

            // 1. JSON 변환
            requestJson = jsonParser.toJson(texts);

            // 2. 직접 API 호출 (기존 로직 유지)
            AiGeminiConstant.InternalInfo info = AiGeminiConstant.internalMap.get(rule);
            String response = aiGeminiClient.call(info.rule(), requestJson, AiGeminiConstant.STRING_LIST_SCHEMA);
            responseJson = AiResponseUtil.extractJsonArray(response);

            // 3. 결과 파싱하여 리스트로 반환 (나머지 검증은 부모가 해줌)
            return jsonParser.parseToList(responseJson, String.class);

        } catch (Exception e) {
            log.error("Translation Executor batch processing failed!");
            log.error("- Request JSON: {}", requestJson);
            log.error("- Response Cleaned: {}", responseJson);
            log.error("- Target Batch: {}", texts, e);

            return Collections.emptyList();
        } finally {
            this.semaphoreRelease();
        }
    }

    @Recover
    public <T extends Translatable> List<T> recoverTranslation(Exception e, List<T> batch, String rule) {
        log.error("[Gemini-Direct] Finally failed after retries! Reason: {}", e.getMessage());
        return Collections.emptyList();
    }

    @Override
    public TranslationType getSupportedType() {
        return TranslationType.DIRECT_GEMINI;
    }

    @Override
    protected String getProviderName() {
        return "Gemini-Direct-Client";
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
