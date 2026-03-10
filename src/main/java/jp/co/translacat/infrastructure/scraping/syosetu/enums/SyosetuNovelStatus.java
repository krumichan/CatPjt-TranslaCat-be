package jp.co.translacat.infrastructure.scraping.syosetu.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum SyosetuNovelStatus {
    SHORT_STORY("短編", "단편", "<ruby>短編<rt>たんぺん</rt></ruby>"),
    ONGOING("連載中", "연재중", "<ruby>連載中<rt>れんさいちゅう</rt></ruby>"),
    COMPLETED("完結済", "완결", "<ruby>完結済<rt>かんけつずみ</rt></ruby>");

    private final String ja;
    private final String ko;
    private final String rubyJa;

    public boolean isShortStory() {
        return this.equals(SHORT_STORY);
    }

    public static SyosetuNovelStatus of(String ja) {
        return Arrays.stream(SyosetuNovelStatus.values())
            .filter(status -> status.getJa().equalsIgnoreCase(ja))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid novel status: [ja]"));
    }
}
