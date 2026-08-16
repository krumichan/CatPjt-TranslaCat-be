package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeywordLocale;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordDisplayName;
import jp.co.translacat.domain.languagelearning.keyword.repository.SystemKeywordLocaleRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SystemKeywordDisplayNameResolver {

    static final String KOREAN_LOCALE = "ko-KR";
    static final String JAPANESE_LOCALE = "ja-JP";

    private final SystemKeywordLocaleRepository localeRepository;

    public Map<Long, KeywordDisplayName> resolve(
            Collection<Long> systemKeywordIds,
            String uiLocale
    ) {
        if (systemKeywordIds.isEmpty()) {
            return Map.of();
        }

        DisplayLocale displayLocale = DisplayLocale.from(uiLocale);
        List<SystemKeywordLocale> rows = localeRepository
                .findAllBySystemKeywordIdInAndLocaleIn(
                        systemKeywordIds,
                        displayLocale.databaseLocales()
                );

        Map<Long, Map<String, String>> namesByKeywordId = new HashMap<>();
        for (SystemKeywordLocale row : rows) {
            namesByKeywordId
                    .computeIfAbsent(
                            row.getSystemKeywordId(),
                            ignored -> new HashMap<>()
                    )
                    .put(row.getLocale(), row.getDisplayName());
        }

        Map<Long, KeywordDisplayName> result = new LinkedHashMap<>();
        for (Long keywordId : systemKeywordIds) {
            Map<String, String> names = namesByKeywordId.get(keywordId);
            if (names == null) continue;

            String primary = names.get(displayLocale.primaryLocale());
            String secondary = displayLocale.secondaryLocale() == null
                    ? null
                    : names.get(displayLocale.secondaryLocale());

            if (primary == null) {
                primary = secondary;
                secondary = null;
            }
            if (primary == null) continue;
            if (primary.equals(secondary)) secondary = null;

            result.put(
                    keywordId,
                    new KeywordDisplayName(primary, secondary)
            );
        }

        return Map.copyOf(result);
    }

    private record DisplayLocale(
            String primaryLocale,
            String secondaryLocale
    ) {
        private static DisplayLocale from(String uiLocale) {
            String normalized = uiLocale == null
                    ? ""
                    : uiLocale.trim()
                            .replace('_', '-')
                            .toLowerCase(Locale.ROOT);

            if (normalized.equals("learning")) {
                return new DisplayLocale(
                        JAPANESE_LOCALE,
                        KOREAN_LOCALE
                );
            }
            if (normalized.equals("ja") || normalized.startsWith("ja-")) {
                return new DisplayLocale(JAPANESE_LOCALE, null);
            }
            return new DisplayLocale(KOREAN_LOCALE, null);
        }

        private List<String> databaseLocales() {
            return secondaryLocale == null
                    ? List.of(primaryLocale)
                    : List.of(primaryLocale, secondaryLocale);
        }
    }
}
