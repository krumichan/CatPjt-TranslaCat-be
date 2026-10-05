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
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public NovelCatalogSourceAdapter(PlatformService platforms, SyosetuParser parser, ObjectMapper mapper) {
        this.platforms = platforms;
        this.parser = parser;
        this.mapper = mapper;
    }

    public JsonNode fetch(NovelCatalogRequest request) {
        long started = System.nanoTime();
        var platform = platforms.getPlatformByCode(PlatformCode.SYOSETU);
        boolean ranking = request.kind().equals("RANKING");
        var template = platforms.getUrlTemplate(platform.getId(), ranking ? PlatformUrlType.RANKING : PlatformUrlType.SEARCH);
        String url = ranking ? String.format(Locale.ROOT, template.getUrlPattern(), request.period().toLowerCase(Locale.ROOT), request.genreId(), request.page())
                : String.format(Locale.ROOT, template.getUrlPattern(), URLEncoder.encode(request.keyword(), StandardCharsets.UTF_8), request.page());
        URI uri = allowedUri(url);
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
                return parse(request, new String(response.body(), StandardCharsets.UTF_8), (System.nanoTime() - started) / 1000000);
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

    public JsonNode parse(NovelCatalogRequest request, String html, long sourceFetchMs) {
        var document = Jsoup.parse(html);
        List<NovelContext> contexts;
        PageNumberContext page;
        if (request.kind().equals("RANKING")) {
            var result = parser.parseNovelRanking(document);
            contexts = result.getNovelContexts();
            page = result.getPageNumberContext();
        } else {
            var result = parser.parseNovelSearch(document);
            contexts = result.getNovelContexts();
            page = result.getPageNumberContext();
        }
        if (contexts.size() > 50) throw new NovelGatewayException(502, "NOVEL_CATALOG_SOURCE_INVALID", false);
        var output = mapper.createObjectNode();
        var cards = output.putArray("cards");
        for (int i = 0; i < contexts.size(); i++) {
            var item = contexts.get(i);
            if (item.getIdentifier() == null || !item.getIdentifier().matches("n[0-9]{1,8}[a-z]{1,4}")) {
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
    private static void forbidden() { throw new NovelGatewayException(422, "NOVEL_CATALOG_SOURCE_FORBIDDEN", false); }
}
