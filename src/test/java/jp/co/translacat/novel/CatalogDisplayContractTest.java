package jp.co.translacat.novel;

import jp.co.translacat.novel.application.CatalogQueryService;
import jp.co.translacat.novel.domain.CatalogModels;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CatalogDisplayContractTest {
    @ParameterizedTest
    @ValueSource(strings = {"RANKING", "SEARCH"})
    void originalCatalogPreservesExistingTranslationUnitContract(String kind) {
        // 준비: 저장 원문은 ja이며, FE의 번역 unit은 기존 ko/en 계약을 사용한다.
        var text = new CatalogModels.Text("猫", "猫", null, null, "ja");
        var item = new CatalogModels.Item("item", "123", 7, 6, "READY", text, text, text, text, "", false, 1, null, true);
        var selector = new CatalogModels.Selector(kind, "daily", "all", "猫", 1, "ja");
        var saved = new CatalogModels.Snapshot("snapshot", 1, "SUCCEEDED", 1, 1, 0, Map.of(),
                selector, List.of(item), "trace", 123);
        var store = mock(CatalogStore.class);
        var ruby = mock(NovelRubyFormatter.class);
        when(store.snapshot(1, "snapshot")).thenReturn(saved);
        when(ruby.markup("猫")).thenReturn("<ruby>猫<rt>ねこ</rt></ruby>");

        // 실행
        var displayed = new CatalogQueryService(store, ruby).snapshot(1, "snapshot");

        // 검증: 원문·상태·selector는 유지하고 존재하지 않는 번역을 만들어 채우지 않는다.
        var expected = new CatalogModels.Text("猫", "<ruby>猫<rt>ねこ</rt></ruby>", null, null, "ko");
        var card = displayed.items().getFirst();
        assertThat(List.of(card.title(), card.author(), card.synopsis(), card.statusText()))
                .containsExactly(expected, expected, expected, expected);
        assertThat(displayed.selector()).isEqualTo(selector);
        assertThat(displayed.state()).isEqualTo("SUCCEEDED");
        assertThat(displayed.ready()).isEqualTo(1);
        assertThat(card.arrivalSequence()).isEqualTo(1);
    }

    @Test
    void rubyFormattingPreservesEnglishTranslationAndLanguageIdentity() {
        // 준비: DB에는 영어로 완료된 제목·소개가 있고 일본어 표시에만 발음이 필요하다.
        var text = new CatalogModels.Text("猫", "猫", null, "Cat", "en");
        var item = new CatalogModels.Item("item", "123", 7, 6, "READY", text, text, text, text, "", false, 1, null, true);
        var saved = new CatalogModels.Snapshot("snapshot", 1, "SUCCEEDED", 1, 1, 0, Map.of(),
                new CatalogModels.Selector("RANKING", "daily", "all", "", 1, "en"), List.of(item), "trace", 123);
        var store = mock(CatalogStore.class);
        var ruby = mock(NovelRubyFormatter.class);
        when(store.snapshot(1, "snapshot")).thenReturn(saved);
        when(ruby.markup("猫")).thenReturn("<ruby>猫<rt>ねこ</rt></ruby>");

        // 실행
        var displayed = new CatalogQueryService(store, ruby).snapshot(1, "snapshot");

        // 검증: 응답 전용 표기 때문에 저장 번역·언어·상태·도착 순서가 소실되지 않는다.
        assertThat(displayed.items().getFirst().title()).isEqualTo(
                new CatalogModels.Text("猫", "<ruby>猫<rt>ねこ</rt></ruby>", null, "Cat", "en"));
        assertThat(displayed.items().getFirst().arrivalSequence()).isEqualTo(1);
        assertThat(displayed.items().getFirst().cached()).isTrue();
        assertThat(displayed.selector()).isEqualTo(saved.selector());
        assertThat(displayed.traceId()).isEqualTo("trace");
    }
}
