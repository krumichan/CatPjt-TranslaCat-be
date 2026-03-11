package jp.co.translacat.infrastructure.client.ai.common;

import jp.co.translacat.domain.novel.translation.model.Translatable;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Slf4j
public abstract class AbstractTranslationExecutor implements AiTranslationProvider {

    @Override
    public <T extends Translatable> List<T> executeTranslation(List<T> batch, String rule) {
        try {
            // 1. 입력 추출
            List<String> japaneseTexts = batch.stream()
                    .map(Translatable::getRawJa)
                    .toList();

            // 2. 추상 메서드: 실제 통신부만 자식 클래스에서 구현
            List<String> translatedTexts = doTranslate(japaneseTexts, rule);

            // 3. 무결성 검증
            if (japaneseTexts.size() != translatedTexts.size()) {
                throw new RuntimeException(String.format(
                        "[%s] Size mismatch: Expected %d, but received %d",
                        getProviderName(), japaneseTexts.size(), translatedTexts.size()
                ));
            }

            // 4. 공통: 결과 배분
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).setKo(translatedTexts.get(i));
            }

            return batch;
        } catch (Exception e) {
            log.error("[{}] Processing failed: {}", getProviderName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    protected abstract List<String> doTranslate(List<String> texts, String rule);
    protected abstract String getProviderName();

    @Override
    public abstract TranslationType getSupportedType();
}
