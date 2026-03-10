package jp.co.translacat.global.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

@Slf4j
@UtilityClass
public class PathUtil {

    public static String extractPath(String url) {
        if (Objects.isNull(url) || url.isBlank()) return "";

        String urlToParse = url.contains("://") ? url : "https://" + url;

        try {
            URI uri = new URI(urlToParse);
            String path = uri.getPath();
            return (path != null) ? path : "";
        } catch (URISyntaxException e) {
            return "";
        }
    }

    public static String getQueryParamFirst(String url, String tagName) {
        try {
            return UriComponentsBuilder.fromUriString(url)
                    .build()
                    .getQueryParams()
                    .getFirst(tagName);
        } catch (Exception e) {
            log.warn("failed to extract query parameters from {}", url, e);
            return null;
        }
    }
}
