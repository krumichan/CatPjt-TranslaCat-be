package jp.co.translacat.domain.imagetranslation;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.*;

@Component
@ConditionalOnProperty(prefix = "translacat.image-translation", name = "enabled", havingValue = "true")
class ImageTranslationLinks {
    private static final String MIMI_HOST = "mimiandeunice.com";
    private static final String MIMI_EPISODE = "https://mimiandeunice.com/2011/06/07/censorship-vs-copyright/";
    private static final int MAX_HTML_BYTES = 2 * MIB;
    private static final int TIMEOUT_MS = 8_000;
    private static final Attribution MIMI_CREDIT = new Attribution("Censorship Vs. Copyright", "Nina Paley",
            MIMI_EPISODE, "CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0/",
            "Translation overlay by TranslaCat under CC BY-SA 3.0. Original artwork is unchanged.");

    private final Set<String> approved;

    // 승인 목록의 정확한 회차만 활성화한다. 임의 URL에 일반 HTML 파서를 연결하지 않는다.
    ImageTranslationLinks(@Value("${translacat.image-translation.approved-links:}") String allowed) {
        this.approved = Arrays.stream(allowed.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    // 요청 URL을 허용된 정식 회차로 제한한다. 미지원 입력은 네트워크 호출 전에 거절한다.
    String canonical(String url) {
        if (url == null || url.length() > 2048 || !MIMI_EPISODE.equals(url) || !approved.contains(url)) {
            throw new RequestFailure(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_LINK");
        }
        return MIMI_EPISODE;
    }

    // 회차의 이미지 후보를 창작자 HTML의 DOM 순서로 작성하고 라이선스를 함께 반환한다.
    Prepared prepare(Job job) throws IOException {
        if (!MIMI_EPISODE.equals(job.sourceLink) || !approved.contains(job.sourceLink)) {
            throw new WorkFailure("LINK_PREPARATION_INVALID", Stage.ACQUIRE, false);
        }
        byte[] html = fetch(URI.create(MIMI_EPISODE), "text/html", MAX_HTML_BYTES, "LINK_PREPARATION_INVALID");
        var document = Jsoup.parse(new String(html, StandardCharsets.UTF_8), MIMI_EPISODE);
        Set<URI> imageUris = new LinkedHashSet<>();

        // 회차 그림만 골라 댓글·아바타·배너는 읽지 않는다.
        for (Element image : document.select("#comic img, #comic-wrap img, .comicpane img,"
                + " .post-bodycopy a[href*=ME_402_CensorshipVsCopyright1] img")) {
            Element link = image.closest("a[href]");
            URI original = link == null ? null : creatorImage(link.absUrl("href"));
            URI inline = original == null ? creatorImage(image.absUrl("src")) : original;
            if (inline != null) {
                imageUris.add(inline);
            }
        }
        if (imageUris.isEmpty() || imageUris.size() > MAX_PAGES) {
            throw new WorkFailure("LINK_PREPARATION_INVALID", Stage.ACQUIRE, false);
        }

        // 준비 단계에서 다운로드 순서 대신 문서의 순서를 고정한다.
        List<Page> pages = new java.util.ArrayList<>();
        for (URI uri : imageUris) {
            Page page = new Page();
            page.id = UUID.randomUUID();
            page.objectId = UUID.randomUUID();
            page.fileName = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
            page.sourcePath = uri.toString();
            page.order = pages.size();
            pages.add(page);
        }
        return new Prepared(pages, List.of(), MIMI_CREDIT);
    }

    // 준비에서 저장한 원본 주소를 다시 검증하고 정지 이미지 바이트만 읽는다.
    byte[] acquire(String source) throws IOException {
        URI uri = creatorImage(source);
        if (uri == null || !approved.contains(MIMI_EPISODE)) {
            throw new WorkFailure("LINK_ASSET_UNAVAILABLE", Stage.ACQUIRE, false);
        }
        return fetch(uri, "image/", MAX_IMAGE_BYTES, "LINK_ASSET_UNAVAILABLE");
    }

    // 창작자 호스트의 게시 이미지 경로만 사용한다. 예전 HTTP 링크도 HTTPS로 해석한다.
    private static URI creatorImage(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            return null;
        }
        try {
            URI input = URI.create(value);
            String scheme = input.getScheme();
            String path = input.getRawPath();
            if (!MIMI_HOST.equals(input.getHost()) || (!"https".equals(scheme) && !"http".equals(scheme))
                    || input.getRawUserInfo() != null || input.getPort() != -1 || input.getRawQuery() != null
                    || input.getRawFragment() != null || path == null || !path.equals(input.normalize().getRawPath())
                    || !path.matches("/wp-content/uploads/[0-9]{4}/[0-9]{2}/[A-Za-z0-9_.-]+")) {
                return null;
            }
            String lower = path.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".webp")
                    && !lower.endsWith(".jpeg")) {
                return null;
            }
            return URI.create("https://" + MIMI_HOST + path);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    // 검증된 HTTPS 주소로만 고정 기한을 쓰고 리디렉션이나 외부 인증 정보는 전달하지 않는다.
    private static byte[] fetch(URI uri, String type, int limit, String failureCode) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "TranslaCat/Webcomic-Preview");
        connection.setRequestProperty("Accept", type.equals("image/") ? "image/png,image/jpeg,image/webp" : type);
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new WorkFailure(failureCode, Stage.ACQUIRE, status == 429 || status >= 500);
            }
            String mediaType = connection.getContentType();
            if (mediaType == null || !mediaType.toLowerCase(Locale.ROOT).startsWith(type)
                    || connection.getContentLengthLong() > limit) {
                throw new WorkFailure(failureCode, Stage.ACQUIRE, false);
            }
            try (var body = connection.getInputStream()) {
                return ImageTranslationInput.limit(body, limit);
            }
        } finally {
            connection.disconnect();
        }
    }
}
