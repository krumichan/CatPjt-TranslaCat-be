package jp.co.translacat.domain.novelgateway;

import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;

public record NovelCatalogRequest(String kind, String period, String genreId, String keyword,
                                  Integer page, String language, String idempotencyKey) {
    public NovelCatalogRequest {
        page = page == null ? 1 : page;
        if (!("RANKING".equals(kind) || "SEARCH".equals(kind)) || page < 1 || page > 10000
                || !java.util.Set.of("ja", "ko", "en").contains(language == null ? "" : language)) invalid();
        // 기존 장르 URL의 _201, _total 등을 그대로 보존한다. 경로·쿼리 구분자는 허용하지 않는다.
        if ("RANKING".equals(kind) && (period == null || !period.matches("[a-zA-Z]{1,20}")
                || genreId == null || !genreId.matches("[A-Za-z0-9_]{1,20}"))) invalid();
        if ("SEARCH".equals(kind) && (keyword == null || keyword.isBlank() || keyword.length() > 100)) invalid();
        token(idempotencyKey);
    }
    public Selector selector() { return new Selector(kind, period, genreId, keyword, page, language); }
    public record Selector(String kind, String period, String genreId, String keyword, int page, String language) {}
    public record Retry(String idempotencyKey) { public Retry { token(idempotencyKey); } }
    public static String platform(String value) {
        if (!("syosetu".equals(value) || "syosyetu".equals(value))) invalid();
        return "syosyetu";
    }
    public static String token(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) invalid();
        return value;
    }
    private static void invalid() { throw new NovelGatewayException(400, "NOVEL_CATALOG_REQUEST_INVALID", false); }
}
