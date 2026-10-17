package jp.co.translacat.novel.domain;

/** 소설의 일본어 원문과 번역 목표 언어를 구분한다. 이전 요청은 한국어로 유지한다. */
public final class NovelTargetLanguage {
    private NovelTargetLanguage() { }

    public static String normalize(String value) {
        if (value == null) return "ko";
        String language = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("ko", "en").contains(language)) {
            throw new NovelProblem("TRANSLATION_TARGET_LANGUAGE_INVALID", 400);
        }
        return language;
    }
}
