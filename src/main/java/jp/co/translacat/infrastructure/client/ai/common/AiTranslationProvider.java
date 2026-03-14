package jp.co.translacat.infrastructure.client.ai.common;

import jp.co.translacat.domain.novel.translation.model.Translatable;
import jp.co.translacat.infrastructure.client.ai.server.AiRuleType;

import java.util.Comparator;
import java.util.List;

public interface AiTranslationProvider {
    <T extends Translatable> List<T> executeTranslation(List<T> batch, AiRuleType rule);
    TranslationType getSupportedType();

    default <T extends Translatable> List<T> executeTranslation(List<T> batch, AiRuleType rule, Comparator<T> comparator) {
        if (comparator != null) {
            batch.sort(comparator);
        }
        return this.executeTranslation(batch, rule);
    }
}
