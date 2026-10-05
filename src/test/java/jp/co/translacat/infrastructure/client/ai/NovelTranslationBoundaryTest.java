package jp.co.translacat.infrastructure.client.ai;

import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.infrastructure.client.ai.common.AbstractTranslationExecutor;
import jp.co.translacat.infrastructure.client.ai.common.AiTranslationProvider;
import jp.co.translacat.infrastructure.client.ai.common.TranslationType;
import jp.co.translacat.infrastructure.client.ai.server.AiRuleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NovelTranslationBoundaryTest {
    @Test
    void singleItemAndLegacyDirectEntryUseOnlyAiServer() {
        // 준비: 구 제공자가 등록되어 있어도 소량 요청을 우회시키면 안 된다.
        AiTranslationProvider server = mock(AiTranslationProvider.class);
        AiTranslationProvider legacy = mock(AiTranslationProvider.class);
        when(server.getSupportedType()).thenReturn(TranslationType.AI_SERVER);
        when(legacy.getSupportedType()).thenReturn(TranslationType.DIRECT_GEMINI);
        var executor = new TranslationExecutor(List.of(server, legacy));
        var units = List.of(TranslationUnit.of("雨が降った。"));
        when(server.executeTranslation(eq(units), eq(AiRuleType.NOVEL), isNull())).thenReturn(units);

        // 실행
        assertThat(executor.executeDirect(units, AiRuleType.NOVEL)).isSameAs(units);

        // 검증: 다른 제공자에 유료 재전송하지 않는다.
        verify(server).executeTranslation(eq(units), eq(AiRuleType.NOVEL), isNull());
        verify(legacy, never()).executeTranslation(anyList(), any(), any());
    }

    @Test
    void providerFailurePropagatesWithoutFallbackOrEmptySuccess() {
        // 준비
        AiTranslationProvider server = mock(AiTranslationProvider.class);
        AiTranslationProvider legacy = mock(AiTranslationProvider.class);
        when(server.getSupportedType()).thenReturn(TranslationType.AI_SERVER);
        when(legacy.getSupportedType()).thenReturn(TranslationType.DIRECT_GEMINI);
        var failure = new IllegalStateException("unavailable");
        when(server.executeTranslation(anyList(), any(), any())).thenThrow(failure);
        var executor = new TranslationExecutor(List.of(server, legacy));

        // 실행 및 검증
        assertThatThrownBy(() -> executor.execute(List.of(TranslationUnit.of("雨。"),
                TranslationUnit.of("夜。")), AiRuleType.NOVEL, TranslationType.AI_SERVER)).isSameAs(failure);
        verify(legacy, never()).executeTranslation(anyList(), any(), any());
    }

    @Test
    void malformedBatchNeverPartiallyMutatesSource() {
        // 준비: 첫 값은 정상이어도 뒤의 잘못된 값 때문에 전체 청크는 실패한다.
        var first = TranslationUnit.of("雨。", "雨。", "이전 번역");
        var second = TranslationUnit.of("夜。");
        var provider = new StubProvider(java.util.Arrays.asList("비.", null));

        // 실행 및 검증
        assertThatThrownBy(() -> provider.executeTranslation(List.of(first, second), AiRuleType.NOVEL))
                .isInstanceOf(IllegalStateException.class);
        assertThat(first.getKo()).isEqualTo("이전 번역");
    }

    @Test
    void wrongCountFailsInsteadOfReturningEmptySuccess() {
        // 준비
        var provider = new StubProvider(List.of());

        // 실행 및 검증
        assertThatThrownBy(() -> provider.executeTranslation(List.of(TranslationUnit.of("雨。")), AiRuleType.NOVEL))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class StubProvider extends AbstractTranslationExecutor {
        private final List<String> response;
        StubProvider(List<String> response) { this.response = response; }
        protected List<String> doTranslate(List<String> texts, AiRuleType rule) { return response; }
        protected String getProviderName() { return "test"; }
        public TranslationType getSupportedType() { return TranslationType.AI_SERVER; }
    }
}
