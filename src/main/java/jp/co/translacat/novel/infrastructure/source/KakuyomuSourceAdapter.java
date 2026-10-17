package jp.co.translacat.novel.infrastructure.source;

import jp.co.translacat.infrastructure.scraping.kakuyomu.KakuyomuUrls;
import jp.co.translacat.infrastructure.scraping.kakuyomu.parser.KakuyomuParser;
import jp.co.translacat.novel.application.NovelExecutionSettings;
import jp.co.translacat.novel.application.NovelPorts;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.infrastructure.http.BoundedHttp;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 카쿠요무 공개 본문만 공통 문장·revision 계약으로 변환한다. */
@Component
public class KakuyomuSourceAdapter implements NovelPorts.Source {
    private static final int MAX_HTML_BYTES = 2_000_000;
    private final KakuyomuParser parser;
    private final SentenceSegmenter segmenter;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public KakuyomuSourceAdapter(KakuyomuParser parser, NovelExecutionSettings settings) {
        this.parser = parser;
        this.segmenter = settings.segmenter();
    }

    @Override
    public SourceEpisode fetch(EpisodeKey key) {
        if (!"kakuyomu".equals(key.platform())) throw new NovelProblem("PLATFORM_NOT_SUPPORTED", 422);
        var uri = KakuyomuUrls.requirePublic(key.sourceUrl());
        try {
            // 주소는 서버가 구성한다. redirect·사설 IP·초과 body는 다른 플랫폼과 같은 경계에서 거부한다.
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new NovelProblem("SOURCE_ADDRESS_FORBIDDEN", 422);
                }
            }
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "TranslaCat-Novel/1.0").GET().build();
            var response = BoundedHttp.send(client, request, MAX_HTML_BYTES, 12_000);
            if (response.statusCode() != 200) {
                throw new NovelProblem(response.statusCode() == 404 ? "SOURCE_NOT_FOUND" : "SOURCE_UNAVAILABLE",
                        response.statusCode() == 404 ? 404 : 502);
            }
            return parse(key, new String(response.body(), StandardCharsets.UTF_8));
        } catch (NovelProblem problem) {
            throw problem;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new NovelProblem("SOURCE_INTERRUPTED", 503);
        } catch (Exception failure) {
            throw new NovelProblem("SOURCE_UNAVAILABLE", 502);
        }
    }

    public SourceEpisode parse(EpisodeKey key, String html) {
        if (!"kakuyomu".equals(key.platform())) throw new NovelProblem("PLATFORM_NOT_SUPPORTED", 422);
        if (html == null || html.length() > MAX_HTML_BYTES) throw new NovelProblem("SOURCE_TOO_LARGE", 413);
        return parser.parseSourceEpisode(Jsoup.parse(html, key.sourceUrl()), key, segmenter);
    }
}
