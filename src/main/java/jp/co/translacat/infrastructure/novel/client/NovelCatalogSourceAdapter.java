package jp.co.translacat.infrastructure.novel.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.common.dto.PageNumberResponseDto;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.PlatformUrlType;
import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.domain.novelgateway.NovelCatalogRequest;
import jp.co.translacat.infrastructure.scraping.syosetu.parser.SyosetuParser;
import jp.co.translacat.infrastructure.scraping.kakuyomu.parser.KakuyomuParser;
import jp.co.translacat.infrastructure.scraping.kakuyomu.KakuyomuUrls;
import jp.co.translacat.novel.domain.EpisodeKey;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 기존 플랫폼 URL 계약으로 원문만 수집한다. 번역/음성/결과 캐시는 NOVEL 책임이다. */
@Component
public class NovelCatalogSourceAdapter {
    private final PlatformService platforms;
    private final SyosetuParser parser;
    private final ObjectMapper mapper;
    private final KakuyomuParser kakuyomu;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public NovelCatalogSourceAdapter(PlatformService platforms, SyosetuParser parser, ObjectMapper mapper) {
        this(platforms, parser, mapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NovelCatalogSourceAdapter(PlatformService platforms, SyosetuParser parser, ObjectMapper mapper,
                                     KakuyomuParser kakuyomu) {
        this.platforms = platforms;
        this.parser = parser;
        this.mapper = mapper;
        this.kakuyomu = kakuyomu;
    }

    public JsonNode fetch(NovelCatalogRequest request) {
        return fetch("syosyetu", request);
    }

    public JsonNode fetch(String platform, NovelCatalogRequest request) {
        long started = System.nanoTime();
        String normalized = NovelCatalogRequest.platform(platform);
        URI uri = sourceUri(normalized, request);
        try {
            // 고정 공개 host만 허용하며 사설 주소/redirect/무제한 body를 통과시키지 않는다.
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) forbidden();
            }
            var http = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "TranslaCat-Novel/1.0").GET().build();
            var pending = client.sendAsync(http, ignored -> new NovelGatewayBodySubscriber());
            try {
                var response = pending.get(12, TimeUnit.SECONDS);
                if (response.statusCode() != 200) throw new NovelGatewayException(502, "NOVEL_CATALOG_SOURCE_UNAVAILABLE", true);
                if (response.body().length > 2000000) throw new NovelGatewayException(413, "NOVEL_CATALOG_SOURCE_TOO_LARGE", false);
                return parse(normalized, request, new String(response.body(), StandardCharsets.UTF_8),
                        (System.nanoTime() - started) / 1000000);
            } finally {
                if (!pending.isDone()) pending.cancel(true);
            }
        } catch (NovelGatewayException failure) {
            throw failure;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new NovelGatewayException(503, "NOVEL_CATALOG_SOURCE_INTERRUPTED", true);
        } catch (Exception failure) {
            throw new NovelGatewayException(502, "NOVEL_CATALOG_SOURCE_UNAVAILABLE", true);
        }
    }

    URI sourceUri(String normalized, NovelCatalogRequest request) {
        boolean kaku = "kakuyomu".equals(normalized);
        var platform = platforms.getPlatformByCode(kaku ? PlatformCode.KAKUYOMU : PlatformCode.SYOSETU);
        boolean ranking = request.kind().equals("RANKING");
        String period = request.period() == null ? null : request.period().toLowerCase(Locale.ROOT);
        if (kaku && "total".equals(period)) period = "entire";
        if (kaku && ranking && !Set.of("daily", "weekly", "monthly", "yearly", "entire").contains(period)) {
            throw new NovelGatewayException(400, "NOVEL_CATALOG_REQUEST_INVALID", false);
        }
        var template = platforms.getUrlTemplate(platform.getId(), ranking ? PlatformUrlType.RANKING : PlatformUrlType.SEARCH);
        // 카쿠요무의 원본 랭킹 100개를 화면의 기존 50개 계약에 맞춰 두 페이지로 나눈다.
        int remotePage = kaku && ranking ? (request.page() + 1) / 2 : request.page();
        String url = ranking ? String.format(Locale.ROOT, template.getUrlPattern(), period, request.genreId(), remotePage)
                : String.format(Locale.ROOT, template.getUrlPattern(), URLEncoder.encode(request.keyword(), StandardCharsets.UTF_8), request.page());
        URI configured = allowedUri(normalized, url);
        if (!kaku && ranking) return syosetuRankingUri(configured, period, request.genreId());
        return configured;
    }

    private URI syosetuRankingUri(URI configured, String period, String genre) {
        // 공개 selector의 _201는 유지한다. 연결형/구분자형 DB template은 수집 경계에서만 같은 type으로 맞춘다.
        String path = configured.getPath();
        if (!(path.startsWith("/rank/list/type/") || path.startsWith("/rank/genrelist/type/"))) return configured;
        String code = genre.startsWith("_") ? genre.substring(1) : genre;
        String prefix = code.matches("[0-9]+") ? "/rank/genrelist/type/" : "/rank/list/type/";
        String query = configured.getRawQuery();
        URI canonical = configured.resolve(prefix + period + "_" + code + "/" + (query == null ? "" : "?" + query));

        // 숫자 장르는 list에서 404이므로 공개 genrelist로 직접 요청한다. host/redirect 허용 범위는 늘리지 않는다.
        return allowedUri(canonical.toString());
    }

    public JsonNode parse(NovelCatalogRequest request, String html, long sourceFetchMs) {
        return parse("syosyetu", request, html, sourceFetchMs);
    }

    public JsonNode parse(String platform, NovelCatalogRequest request, String html, long sourceFetchMs) {
        String normalized = NovelCatalogRequest.platform(platform);
        boolean kaku = "kakuyomu".equals(normalized);
        if (html == null || html.length() > 2000000) throw new NovelGatewayException(413, "NOVEL_CATALOG_SOURCE_TOO_LARGE", false);
        String base = "https://yomou.syosetu.com";
        if (kaku) {
            String period = request.period() == null ? null : request.period().toLowerCase(Locale.ROOT);
            if ("total".equals(period)) period = "entire";
            base = request.kind().equals("RANKING")
                    ? "https://kakuyomu.jp/rankings/" + request.genreId() + "/" + period
                        + "?work_variation=all&page=" + ((request.page() + 1) / 2)
                    : "https://kakuyomu.jp/search?q=" + URLEncoder.encode(request.keyword(), StandardCharsets.UTF_8)
                        + "&page=" + request.page();
        }
        var document = Jsoup.parse(html, base);
        List<NovelContext> contexts;
        PageNumberContext page;
        if (request.kind().equals("RANKING")) {
            var result = kaku ? kakuyomu.parseNovelRanking(document) : parser.parseNovelRanking(document);
            contexts = result.getNovelContexts();
            page = result.getPageNumberContext();
        } else {
            var result = kaku ? kakuyomu.parseNovelSearch(document) : parser.parseNovelSearch(document);
            contexts = result.getNovelContexts();
            page = result.getPageNumberContext();
        }
        if (kaku && request.kind().equals("RANKING")) {
            if (contexts.size() > 100) throw new NovelGatewayException(502, "NOVEL_CATALOG_SOURCE_INVALID", false);
            int offset = request.page() % 2 == 0 ? 50 : 0;
            boolean more = contexts.size() > offset + 50 || page.getNext() != null;
            contexts = contexts.subList(Math.min(offset, contexts.size()), Math.min(offset + 50, contexts.size()));
            page = PageNumberContext.builder().current(request.page()).first(request.page() > 1 ? 1 : null)
                    .prev(request.page() > 1 ? request.page() - 1 : null).next(more ? request.page() + 1 : null)
                    .pages(List.of(request.page())).build();
        }
        if (contexts.size() > 50) throw new NovelGatewayException(502, "NOVEL_CATALOG_SOURCE_INVALID", false);
        var output = mapper.createObjectNode();
        var cards = output.putArray("cards");
        for (int i = 0; i < contexts.size(); i++) {
            var item = contexts.get(i);
            if (item.getIdentifier() == null || !(kaku ? EpisodeKey.numericId(item.getIdentifier())
                    : item.getIdentifier().matches("n[0-9]{1,8}[a-z]{1,4}"))) {
                throw new NovelGatewayException(502, "NOVEL_CATALOG_SOURCE_INVALID", false);
            }
            cards.addObject().put("identifier", item.getIdentifier()).put("rank", item.getRank()).put("sourcePosition", i)
                    .put("title", item.getTitle().getRawJa()).put("author", item.getAuthor().getRawJa())
                    .put("synopsis", item.getSynopsis().getRawJa()).put("statusText", item.getStatus().getRawJa())
                    .put("genreText", item.getGenreText()).put("isShortStory", item.isShortStory());
        }
        output.set("pageInfo", mapper.valueToTree(PageNumberResponseDto.of(page)));
        output.put("sourceFetchMs", sourceFetchMs);
        return output;
    }

    static URI allowedUri(String value) {
        URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException failure) { forbidden(); return null; }
        if (!"https".equals(uri.getScheme()) || !"yomou.syosetu.com".equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getPort() != -1
                || !(uri.getPath().startsWith("/rank/") || uri.getPath().startsWith("/search.php"))) forbidden();
        return uri;
    }
    static URI allowedUri(String platform, String value) {
        if (!"kakuyomu".equals(platform)) return allowedUri(value);
        URI uri;
        try { uri = KakuyomuUrls.requirePublic(value); }
        catch (IllegalArgumentException failure) { forbidden(); return null; }
        if (!(uri.getPath().matches("/rankings/[a-z_]{1,30}/(daily|weekly|monthly|yearly|entire)")
                || uri.getPath().equals("/search"))) forbidden();
        return uri;
    }
    private static void forbidden() { throw new NovelGatewayException(422, "NOVEL_CATALOG_SOURCE_FORBIDDEN", false); }
}
