package jp.co.translacat.novel.domain;

import java.util.List;
import java.util.Map;

/** 문자 정보가 전혀 없는 대체값을 거부한다. 번역의 의미나 목표 언어를 판정하지 않는다. */
public final class TranslationContentGuard {
    private TranslationContentGuard() {}
    public static void validate(List<SourceEpisode.Segment> source, Map<String, String> translated,
                                TranslationOptions.ValidationPolicy policy) {
        if (policy != TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2) return;
        for (var segment : source) {
            String text = translated.get(segment.id());
            // 보조 평면을 포함한 Unicode 문자(L*)와 십진 숫자(Nd)를 코드 포인트 단위로 확인한다.
            if (segment.plainJa().codePoints().anyMatch(Character::isLetterOrDigit)
                    && (text == null || text.codePoints().noneMatch(Character::isLetterOrDigit))) {
                throw new NovelProblem("TRANSLATION_MINIMUM_CONTENT_INVALID", 502, true, 0);
            }
        }
    }
}
