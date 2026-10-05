package jp.co.translacat.infrastructure.client.ai.server;

import jp.co.translacat.infrastructure.client.ai.common.AbstractTranslationExecutor;
import jp.co.translacat.infrastructure.client.ai.common.TranslationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerTranslationExecutor extends AbstractTranslationExecutor {
    private final AiServerClient aiServerClient;

    @Override
    protected List<String> doTranslate(List<String> texts, AiRuleType rule) {
        return aiServerClient.callBatchTranslation(texts, rule.getValue());
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
