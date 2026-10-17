package jp.co.translacat.infrastructure.scraping.kakuyomu;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 공개 카쿠요무 작품·목록 경로만 허용한다. 식별자는 정수로 변환하지 않는다. */
public final class KakuyomuUrls {
    private static final String ORIGIN = "https://kakuyomu.jp";
    private static final Pattern WORK = Pattern.compile("^/works/([1-9][0-9]{0,31})(?:/episodes/([1-9][0-9]{0,31}))?/?$");
    private static final Pattern RANKING = Pattern.compile("^/rankings/(?:all|fantasy|action|sf|love_story|romance|drama|horror|mystery|nonfiction|history|criticism|others)/(?:daily|weekly|monthly|yearly|entire)/?$");

    private KakuyomuUrls() {}

    public static URI requirePublic(String url) {
        // host·경로는 외부 응답의 링크에도 다시 적용한다. 사용자 정보·포트·fragment는 사용하지 않는다.
        if (url == null || url.length() > 4096 || url.indexOf('\\') >= 0) throw invalid();
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
        if (!"https".equals(uri.getScheme()) || !"kakuyomu.jp".equals(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getRawPath() == null || uri.getRawPath().contains("%")
                || !(WORK.matcher(uri.getPath()).matches() || RANKING.matcher(uri.getPath()).matches()
                || uri.getPath().matches("/search/?"))) throw invalid();
        parameters(uri);
        return uri;
    }

    public static String workUrl(String workId) {
        return ORIGIN + "/works/" + requireId(workId);
    }

    public static String episodeUrl(String workId, String episodeId) {
        return workUrl(workId) + "/episodes/" + requireId(episodeId);
    }

    public static String workId(String url) {
        Matcher match = WORK.matcher(requirePublic(url).getPath());
        if (!match.matches()) throw invalid();
        return match.group(1);
    }

    public static String episodeId(String url, String workId) {
        URI uri = requirePublic(url);
        Matcher match = WORK.matcher(uri.getPath());
        if (!match.matches() || !requireId(workId).equals(match.group(1)) || match.group(2) == null
                || uri.getRawQuery() != null) throw invalid();
        return match.group(2);
    }

    public static Optional<String> pagination(String currentUrl, String href) {
        return samePage(currentUrl, href).filter(candidate -> pageNumber(candidate) == pageNumber(currentUrl) + 1);
    }

    public static Optional<String> samePage(String currentUrl, String href) {
        // 다음 페이지가 검색어·장르·기간을 바꾸거나 외부 host로 이동하지 못하게 한다.
        try {
            URI current = requirePublic(currentUrl);
            if (href == null || href.isBlank() || href.indexOf('\\') >= 0) return Optional.empty();
            URI candidate = requirePublic(current.resolve(href).toString());
            if (!current.getPath().equals(candidate.getPath())) return Optional.empty();
            Map<String, String> before = new LinkedHashMap<>(parameters(current));
            Map<String, String> after = new LinkedHashMap<>(parameters(candidate));
            before.remove("page");
            after.remove("page");
            return before.equals(after) ? Optional.of(candidate.toString()) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    public static int pageNumber(String url) {
        return Integer.parseInt(parameters(requirePublic(url)).getOrDefault("page", "1"));
    }

    private static Map<String, String> parameters(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        if (uri.getRawQuery() == null || uri.getRawQuery().isEmpty()) return values;
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (key.isBlank() || values.putIfAbsent(key, value) != null) throw invalid();
        }
        String page = values.get("page");
        if (page != null && (!page.matches("[1-9][0-9]{0,5}") || Integer.parseInt(page) > 100000)) throw invalid();
        return values;
    }

    private static String requireId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,31}")) throw invalid();
        return id;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("KAKUYOMU_URL_INVALID");
    }
}
