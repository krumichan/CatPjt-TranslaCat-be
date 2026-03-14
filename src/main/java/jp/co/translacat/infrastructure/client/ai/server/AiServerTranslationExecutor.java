package jp.co.translacat.infrastructure.client.ai.server;

import jp.co.translacat.domain.novel.translation.model.Translatable;
import jp.co.translacat.infrastructure.client.ai.common.AbstractTranslationExecutor;
import jp.co.translacat.infrastructure.client.ai.common.TranslationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerTranslationExecutor extends AbstractTranslationExecutor {
    private final AiServerClient aiServerClient;

    @Override
    @Retryable(
            retryFor = { Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 1.5, maxDelay = 2000),
            recover = "recoverTranslation"
    )
    public <T extends Translatable> List<T> executeTranslation(List<T> batch, AiRuleType rule) {
        return super.executeTranslation(batch, rule);
    }

    @Override
    protected List<String> doTranslate(List<String> texts, AiRuleType rule) {
        return aiServerClient.callBatchTranslation(texts, rule.getValue());
    }

    @Recover
    public <T extends Translatable> List<T> recoverTranslation(Exception e, List<T> batch, String rule) {
        log.error("[Ai-Server] Finally failed after retries! Reason: {}, Rule: {}", e.getMessage(), rule);
        return Collections.emptyList();
    }

    @Override
    public TranslationType getSupportedType() {
        return TranslationType.AI_SERVER;
    }

    @Override
    protected String getProviderName() {
        return "Python-AI-Server";
    }
}
