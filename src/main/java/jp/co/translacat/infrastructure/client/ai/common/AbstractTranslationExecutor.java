package jp.co.translacat.infrastructure.client.ai.common;

import jp.co.translacat.domain.novel.translation.model.Translatable;
import jp.co.translacat.infrastructure.client.ai.server.AiRuleType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public abstract class AbstractTranslationExecutor implements AiTranslationProvider {

    @Override
    public <T extends Translatable> List<T> executeTranslation(List<T> batch, AiRuleType rule) {
        if (batch.isEmpty()) return batch;
        // 외부 실행 실패를 빈 정상 결과로 바꾸지 않는다.
        List<String> japaneseTexts = batch.stream().map(Translatable::getRawJa).toList();
        if (japaneseTexts.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Translation source must be text.");
        }
        List<String> translatedTexts = doTranslate(japaneseTexts, rule);

        // 모든 결과를 먼저 확인하여 실패한 청크가 기존 번역을 부분 변경하지 않게 한다.
        if (translatedTexts == null || japaneseTexts.size() != translatedTexts.size()) {
            throw new IllegalStateException("Translation result count does not match the request.");
        }
        for (int i = 0; i < translatedTexts.size(); i++) {
            String translated = translatedTexts.get(i);
            if (translated == null || translated.length() > 100_000
                    || (!japaneseTexts.get(i).isBlank() && translated.isBlank())) {
                throw new IllegalStateException("Translation result contains invalid text.");
            }
        }
        for (int i = 0; i < batch.size(); i++) batch.get(i).setKo(translatedTexts.get(i));
        return batch;
    }

    protected abstract List<String> doTranslate(List<String> texts, AiRuleType rule);

    protected abstract String getProviderName();

    @Override
    public abstract TranslationType getSupportedType();
}
