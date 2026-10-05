package jp.co.translacat.novel.infrastructure.source;

import jp.co.translacat.novel.application.NovelPorts;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.infrastructure.http.BoundedHttp;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class SyosyetuSourceAdapter implements NovelPorts.Source {
    private SentenceSegmenter segmenter = new SentenceSegmenter();
    @org.springframework.beans.factory.annotation.Autowired
    public void configureSegmentation(jp.co.translacat.novel.application.NovelExecutionSettings settings) {
        segmenter = settings.segmenter();
    }
    private static final int MAX_HTML_BYTES = 2_000_000;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public SourceEpisode fetch(EpisodeKey key) {
        // 주소는 검증된 공개 식별자로만 구성한다. 외부 링크 및 redirect는 따라가지 않는다.
        URI uri = URI.create(key.sourceUrl());
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new NovelProblem("SOURCE_ADDRESS_FORBIDDEN", 422);
                }
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "TranslaCat-Novel/1.0").GET().build();
            HttpResponse<byte[]> response = BoundedHttp.send(client, request, MAX_HTML_BYTES, 12_000);
            if (response.statusCode() != 200) {
                throw new NovelProblem(response.statusCode() == 404 ? "SOURCE_NOT_FOUND" : "SOURCE_UNAVAILABLE",
                        response.statusCode() == 404 ? 404 : 502);
            }
            return parse(key, new String(response.body(), StandardCharsets.UTF_8));
        } catch (NovelProblem problem) {
            throw problem;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NovelProblem("SOURCE_INTERRUPTED", 503);
        } catch (Exception exception) {
            throw new NovelProblem("SOURCE_UNAVAILABLE", 502);
        }
    }

    public SourceEpisode parse(EpisodeKey key, String html) {
        if (html == null || html.length() > MAX_HTML_BYTES) {
            throw new NovelProblem("SOURCE_TOO_LARGE", 413);
        }
        Document document = Jsoup.parse(html, key.sourceUrl());
        Element title = document.selectFirst(".p-novel__title, .novel_subtitle");
        List<String> paragraphs = new ArrayList<>();
        List<List<SourceEpisode.RubyToken>> ruby = new ArrayList<>();

        // 본문만 대상이다. ruby 발음은 제거하고 escaped '<...>' 일반 텍스트는 그대로 보존한다.
        for (Element paragraph : document.select(".js-novel-text p, #novel_honbun p")) {
            if (paragraph.id().startsWith("Lp") || paragraph.id().startsWith("La")
                    || paragraph.parents().stream().anyMatch(parent -> parent.hasClass("p-novel__text--preface")
                    || parent.hasClass("p-novel__text--afterword"))) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            List<SourceEpisode.RubyToken> tokens = new ArrayList<>();
            paragraph.childNodes().forEach(node -> appendText(node, text, tokens));
            paragraphs.add(text.toString());
            ruby.add(tokens);
        }
        if (title == null || paragraphs.isEmpty()) {
            throw new NovelProblem("SOURCE_PAGE_NOT_SUPPORTED", 422);
        }
        return segmenter.segment(key, title.text(), paragraphs,
                pagerId(key, document.selectFirst(".c-pager__item--before")),
                pagerId(key, document.selectFirst(".c-pager__item--next")), ruby);
    }

    private void appendText(org.jsoup.nodes.Node node, StringBuilder text, List<SourceEpisode.RubyToken> tokens) {
        if (node instanceof org.jsoup.nodes.TextNode value) {
            text.append(value.getWholeText());
            return;
        }
        if (!(node instanceof Element element) || List.of("rt", "rp", "script", "style", "iframe", "object").contains(element.tagName())) {
            return;
        }
        if (element.tagName().equals("br")) {
            text.append('\n');
            return;
        }
        if (element.tagName().equals("ruby")) {
            Element base = element.clone();
            String reading = base.select("rt").stream().map(Element::text).collect(java.util.stream.Collectors.joining());
            base.select("rt,rp,script,style,iframe,object").remove();
            String value = base.wholeText();
            int start = text.length();
            text.append(value);
            if (!value.isEmpty() && !reading.isBlank()) {
                tokens.add(new SourceEpisode.RubyToken(start, text.length(), value, reading));
            }
            return;
        }
        element.childNodes().forEach(child -> appendText(child, text, tokens));
    }

    private String pagerId(EpisodeKey key, Element link) {
        if (link == null) {
            return null;
        }
        try {
            URI uri = URI.create(key.sourceUrl()).resolve(link.attr("href"));
            if (!"https".equals(uri.getScheme()) || !"ncode.syosetu.com".equals(uri.getHost())
                    || uri.getPort() != -1 || uri.getRawQuery() != null || uri.getUserInfo() != null) {
                return null;
            }
            String prefix = "/" + key.novel() + "/";
            if (!uri.getPath().matches(java.util.regex.Pattern.quote(prefix) + "[1-9][0-9]{0,7}/?")) {
                return null;
            }
            return uri.getPath().substring(prefix.length()).replace("/", "");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
