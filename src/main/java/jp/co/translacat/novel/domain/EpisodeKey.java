package jp.co.translacat.novel.domain;

import java.util.Locale;

public record EpisodeKey(String platform, String novel, String episode) {
    public EpisodeKey {
        if (platform == null || novel == null || episode == null) {
            throw new NovelProblem("EPISODE_ID_INVALID", 400);
        }
        platform = platform.toLowerCase(Locale.ROOT);
        if (!platform.equals("syosetu") && !platform.equals("syosyetu")) {
            throw new NovelProblem("PLATFORM_NOT_SUPPORTED", 422);
        }
        // 공개 별칭을 허용하되 같은 원문은 같은 저장소 정체성을 갖는다.
        platform = "syosyetu";
        novel = novel.toLowerCase(Locale.ROOT);
        if (!novel.matches("n[0-9]{1,8}[a-z]{1,4}")
                || !episode.matches("short|0|[1-9][0-9]{0,7}")) {
            throw new NovelProblem("EPISODE_ID_INVALID", 400);
        }
        if (episode.equals("0")) {
            episode = "short";
        }
    }

    public String value() { return platform + ":" + novel + ":" + episode; }
    public String sourceUrl() {
        return "https://ncode.syosetu.com/" + novel + "/" + (episode.equals("short") ? "" : episode + "/");
    }
}
