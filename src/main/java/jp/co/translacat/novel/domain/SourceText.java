package jp.co.translacat.novel.domain;

/** FE String.trim과 같은 공백 정의. 원문은 보존하고 번역 target/진행 가중치만 판정한다. */
public final class SourceText {
    private SourceText() {}
    public static int eligibleCodePoints(String value) {
        return isBlank(value) ? 0 : value.codePointCount(0, value.length());
    }
    public static boolean isBlank(String value) {
        return value.codePoints().allMatch(cp -> cp >= 0x09 && cp <= 0x0d || cp == 0x20 || cp == 0xa0
                || cp == 0x1680 || cp >= 0x2000 && cp <= 0x200a || cp == 0x2028 || cp == 0x2029
                || cp == 0x202f || cp == 0x205f || cp == 0x3000 || cp == 0xfeff);
    }
}
