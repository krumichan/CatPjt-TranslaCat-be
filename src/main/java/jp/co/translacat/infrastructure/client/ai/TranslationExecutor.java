package jp.co.translacat.infrastructure.client.ai;

import jp.co.translacat.domain.novel.translation.model.Translatable;
import jp.co.translacat.infrastructure.client.ai.common.AiTranslationProvider;
import jp.co.translacat.infrastructure.client.ai.common.TranslationType;
import jp.co.translacat.infrastructure.client.ai.server.AiRuleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TranslationExecutor {
    private final Map<TranslationType, AiTranslationProvider> providerMap;

    public TranslationExecutor(List<AiTranslationProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(AiTranslationProvider::getSupportedType, p -> p));
    }

    public <T extends Translatable> List<T> executeDirect(List<T> batch, AiRuleType rule) {
        return this.execute(batch, rule, TranslationType.DIRECT_GEMINI, null);
    }

    public <T extends Translatable> List<T> execute(List<T> batch, AiRuleType rule, TranslationType type) {
        return this.execute(batch, rule, type, null);
    }

    public <T extends Translatable> List<T> execute(List<T> batch, AiRuleType rule, TranslationType type, Comparator<T> comparator) {
        log.info("Request to translate to AI Server.");
        log.info("Targets: {}", batch);

        TranslationType finalType = type;
        if (batch.size() <= 1) {
            log.debug("[Router] Small batch detected. Diverting to DIRECT_GEMINI for efficiency.");
            finalType = TranslationType.DIRECT_GEMINI;
        }

        try {
            return providerMap.get(finalType).executeTranslation(batch, rule, comparator);
        } catch (Exception e) {
            log.error("[Router] Primary path ({}) failed. Falling back to DIRECT_GEMINI.", finalType, e);
            return providerMap.get(TranslationType.DIRECT_GEMINI).executeTranslation(batch, rule, comparator);
        }
    }
}
