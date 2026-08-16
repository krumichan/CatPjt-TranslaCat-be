package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeywordLocale;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordDisplayName;
import jp.co.translacat.domain.languagelearning.keyword.repository.SystemKeywordLocaleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemKeywordDisplayNameResolverTest {

    @Mock
    private SystemKeywordLocaleRepository localeRepository;

    private SystemKeywordDisplayNameResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SystemKeywordDisplayNameResolver(localeRepository);
    }

    @Test
    void resolvesKoreanNameForKoreanHeaderLocale() {
        when(localeRepository.findAllBySystemKeywordIdInAndLocaleIn(
                List.of(1L),
                List.of("ko-KR")
        )).thenReturn(List.of(
                SystemKeywordLocale.of(1L, "ko-KR", "쇼핑")
        ));

        Map<Long, KeywordDisplayName> result = resolver.resolve(
                List.of(1L),
                "ko"
        );

        assertThat(result.get(1L).primary()).isEqualTo("쇼핑");
        assertThat(result.get(1L).secondary()).isNull();
    }

    @Test
    void resolvesJapaneseNameForJapaneseHeaderLocale() {
        when(localeRepository.findAllBySystemKeywordIdInAndLocaleIn(
                List.of(1L),
                List.of("ja-JP")
        )).thenReturn(List.of(
                SystemKeywordLocale.of(1L, "ja-JP", "ショッピング")
        ));

        Map<Long, KeywordDisplayName> result = resolver.resolve(
                List.of(1L),
                "ja"
        );

        assertThat(result.get(1L).primary()).isEqualTo("ショッピング");
        assertThat(result.get(1L).secondary()).isNull();
    }

    @Test
    void learningLocaleReturnsJapanesePrimaryAndKoreanSecondary() {
        when(localeRepository.findAllBySystemKeywordIdInAndLocaleIn(
                List.of(1L),
                List.of("ja-JP", "ko-KR")
        )).thenReturn(List.of(
                SystemKeywordLocale.of(1L, "ko-KR", "가격 비교"),
                SystemKeywordLocale.of(1L, "ja-JP", "価格比較")
        ));

        Map<Long, KeywordDisplayName> result = resolver.resolve(
                List.of(1L),
                "learning"
        );

        assertThat(result.get(1L).primary()).isEqualTo("価格比較");
        assertThat(result.get(1L).secondary()).isEqualTo("가격 비교");
    }

    @Test
    void learningLocaleFallsBackToKoreanWhenJapaneseNameIsMissing() {
        when(localeRepository.findAllBySystemKeywordIdInAndLocaleIn(
                List.of(1L),
                List.of("ja-JP", "ko-KR")
        )).thenReturn(List.of(
                SystemKeywordLocale.of(1L, "ko-KR", "가격 비교")
        ));

        Map<Long, KeywordDisplayName> result = resolver.resolve(
                List.of(1L),
                "learning"
        );

        assertThat(result.get(1L).primary()).isEqualTo("가격 비교");
        assertThat(result.get(1L).secondary()).isNull();
    }

    @Test
    void unknownHeaderLocaleDefaultsToKorean() {
        when(localeRepository.findAllBySystemKeywordIdInAndLocaleIn(
                List.of(1L),
                List.of("ko-KR")
        )).thenReturn(List.of(
                SystemKeywordLocale.of(1L, "ko-KR", "쇼핑")
        ));

        Map<Long, KeywordDisplayName> result = resolver.resolve(
                List.of(1L),
                "en-US"
        );

        assertThat(result.get(1L).primary()).isEqualTo("쇼핑");
    }
}
