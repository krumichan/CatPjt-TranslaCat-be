package jp.co.translacat.novel.domain;

import java.util.Locale;

public record EpisodeKey(String platform, String novel, String episode) {
    public EpisodeKey {
        if (platform == null || novel == null || episode == null) {
            throw new NovelProblem("EPISODE_ID_INVALID", 400);
        }
        platform = normalizePlatform(platform);
        // 카쿠요무 ID는 JS number와 signed long 범위를 넘을 수 있어 끝까지 문자열로 보존한다.
        if (platform.equals("kakuyomu")) {
            if (!numericId(novel) || !numericId(episode)) throw new NovelProblem("EPISODE_ID_INVALID", 400);
        } else {
            novel = novel.toLowerCase(Locale.ROOT);
            if (!novel.matches("n[0-9]{1,8}[a-z]{1,4}")
                    || !episode.matches("short|0|[1-9][0-9]{0,7}")) {
                throw new NovelProblem("EPISODE_ID_INVALID", 400);
            }
            if (episode.equals("0")) episode = "short";
        }
    }

    public static String normalizePlatform(String value) {
        if (value == null) throw new NovelProblem("PLATFORM_NOT_SUPPORTED", 422);
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "syosetu", "syosyetu" -> "syosyetu";
            case "kakuyomu" -> "kakuyomu";
            default -> throw new NovelProblem("PLATFORM_NOT_SUPPORTED", 422);
        };
    }

    public static boolean numericId(String value) { return value != null && value.matches("[1-9][0-9]{0,31}"); }

    public String value() { return platform + ":" + novel + ":" + episode; }
    public String indexUrl() {
        return platform.equals("kakuyomu") ? "https://kakuyomu.jp/works/" + novel
                : "https://ncode.syosetu.com/" + novel + "/";
    }
    public String sourceUrl() {
        return platform.equals("kakuyomu") ? indexUrl() + "/episodes/" + episode
                : indexUrl() + (episode.equals("short") ? "" : episode + "/");
    }
}
