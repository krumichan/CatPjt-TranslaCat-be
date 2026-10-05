package jp.co.translacat.domain.novelgateway;

import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;

public record NovelCatalogRequest(String kind, String period, String genreId, String keyword,
                                  Integer page, String language, String idempotencyKey) {
    public NovelCatalogRequest {
        page = page == null ? 1 : page;
        if (!("RANKING".equals(kind) || "SEARCH".equals(kind)) || page < 1 || page > 10000
                || !("ja".equals(language) || "ko".equals(language))) invalid();
        if ("RANKING".equals(kind) && (period == null || !period.matches("[a-zA-Z]{1,20}")
                || genreId == null || !genreId.matches("[A-Za-z0-9]{1,20}"))) invalid();
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
