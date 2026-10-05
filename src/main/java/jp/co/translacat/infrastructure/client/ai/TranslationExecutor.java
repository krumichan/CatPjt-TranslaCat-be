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
        // 기존 호출자의 메서드는 보존하되 외부 제공자 직접 실행은 금지한다.
        return this.execute(batch, rule, TranslationType.AI_SERVER, null);
    }

    public <T extends Translatable> List<T> execute(List<T> batch, AiRuleType rule, TranslationType type) {
        return this.execute(batch, rule, type, null);
    }

    public <T extends Translatable> List<T> execute(List<T> batch, AiRuleType rule, TranslationType type,
                                                    Comparator<T> comparator) {
        if (type != TranslationType.AI_SERVER) {
            throw new IllegalArgumentException("Only the OpenAI-backed AI service is allowed.");
        }
        if (batch.isEmpty()) return batch;
        var provider = providerMap.get(TranslationType.AI_SERVER);
        if (provider == null) throw new IllegalStateException("AI service provider is unavailable.");

        // 원문/응답은 로그에 남기지 않고, 재전송·제공자 폴백 없이 실패를 호출자에게 전달한다.
        log.info("Novel legacy translation: items={}, rule={}", batch.size(), rule);
        return provider.executeTranslation(batch, rule, comparator);
    }
}
