package jp.co.translacat.infrastructure.client.ai.gemini;

import jp.co.translacat.domain.novel.translation.model.Translatable;
import jp.co.translacat.global.utils.BatchProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiBatchService {
    private final BatchProcessor batchProcessor;
    private final GeminiTranslationExecutor translationExecutor;

    public <T extends Translatable> T processWithAiGemini(T source, String rule) {
        List<T> result = processWithAiGemini(Collections.singletonList(source), 1, rule, null);
        return result.isEmpty() ? null : result.getFirst();
    }

    public <T extends Translatable> T processWithAiGemini(List<T> source, String rule) {
        List<T> result = processWithAiGemini(source, source.size(), rule, null);
        return result.isEmpty() ? null : result.getFirst();
    }

    public <T extends Translatable> List<T> processWithAiGemini(
            List<T> source,
            int batchSize,
            String rule
    ) {
        return batchProcessor.processParallel(
                source,
                batchSize,
                batch -> this.translationExecutor.executeTranslation(batch, rule),
                null
        );
    }

    public <T extends Translatable> List<T> processWithAiGemini(
            List<T> source,
            int batchSize,
            String rule,
            Comparator<T> comparator
    ) {
        return batchProcessor.processParallel(
                source,
                batchSize,
                batch -> this.translationExecutor.executeTranslation(batch, rule),
                comparator
        );
    }
}
