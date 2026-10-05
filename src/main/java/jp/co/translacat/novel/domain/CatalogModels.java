package jp.co.translacat.novel.domain;

import java.util.List;
import java.util.Map;

/** 목록의 위치와 작품 번역의 재사용 identity를 분리한다. */
public final class CatalogModels {
    private CatalogModels() {}

    public record Selector(String kind, String period, String genreId, String keyword, int page, String language) {
        public Selector {
            if (!("RANKING".equals(kind) || "SEARCH".equals(kind)) || page < 1 || page > 10000
                    || !("ja".equals(language) || "ko".equals(language))) invalid();
            if ("RANKING".equals(kind) && (period == null || !period.matches("[a-zA-Z]{1,20}")
                    || genreId == null || !genreId.matches("[A-Za-z0-9]{1,20}"))) invalid();
            if ("SEARCH".equals(kind) && (keyword == null || keyword.isBlank() || keyword.length() > 100)) invalid();
        }
    }

    public record SourceCard(String identifier, int rank, int sourcePosition, String title, String author,
                             String synopsis, String statusText, String genreText, boolean isShortStory) {
        public SourceCard {
            if (identifier == null || !identifier.matches("n[0-9]{1,8}[a-z]{1,4}")
                    || rank < 0 || sourcePosition < 0 || sourcePosition > 49
                    || title == null || title.isBlank() || title.length() > 1000) invalid();
            author = bounded(author, 500);
            synopsis = bounded(synopsis, 12000);
            statusText = bounded(statusText, 500);
            genreText = bounded(genreText, 500);
        }

        public Map<String, String> text() {
            return Map.of("title", title, "author", author, "synopsis", synopsis, "statusText", statusText);
        }
    }

    public record SourcePage(List<SourceCard> cards, Map<String, Object> pageInfo, long sourceFetchMs) {
        public SourcePage {
            if (cards == null || cards.size() > 50 || cards.stream().map(SourceCard::identifier).distinct().count() != cards.size()
                    || cards.stream().mapToInt(c -> c.title().length() + c.author().length() + c.synopsis().length()).sum() > 90000) invalid();
            cards = List.copyOf(cards);
            pageInfo = pageInfo == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(pageInfo));
        }
    }

    public record Start(Selector selector, String idempotencyKey, SourcePage source) {}
    public record Retry(String idempotencyKey) {}
    public record Text(String rawJa, String ja, String ko) {}
    public record Item(String itemId, String identifier, int rank, int sourcePosition, String status,
                       Text title, Text author, Text synopsis, Text statusText, String genreText,
                       boolean isShortStory, long arrivalSequence, String errorCode, boolean cached) {}
    public record Snapshot(String snapshotId, long eventSequence, String state, int total, int ready, int failed,
                           Map<String, Object> pageInfo, Selector selector, List<Item> items,
                           String traceId, long sourceFetchMs) {}

    private static String bounded(String value, int max) {
        if (value == null) return "";
        if (value.length() > max) invalid();
        return value;
    }

    private static void invalid() { throw new NovelProblem("CATALOG_REQUEST_INVALID", 400); }
}
