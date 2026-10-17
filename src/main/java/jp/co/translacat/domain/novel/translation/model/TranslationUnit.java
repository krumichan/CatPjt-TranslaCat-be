package jp.co.translacat.domain.novel.translation.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class TranslationUnit implements Translatable {
    private String rawJa;
    private String ja;
    private String ko;
    private String targetLanguage = "ko";
    private String translatedText;

    public TranslationUnit(String rawJa, String ja, String ko) {
        this.rawJa = rawJa;
        this.ja = ja;
        this.ko = ko;
    }

    public String getTranslatedText() {
        return "ko".equals(targetLanguage) ? ko : translatedText;
    }

    public TranslationUnit forTarget(String targetLanguage, String translatedText) {
        String target = jp.co.translacat.novel.domain.NovelTargetLanguage.normalize(targetLanguage);
        // 표시용 사본만 바꾼다. 기존 한국어 DB/수집 문맥을 영어 값으로 덮어쓰지 않는다.
        TranslationUnit copy = new TranslationUnit(rawJa, ja, "ko".equals(target) ? ko : null);
        copy.targetLanguage = target;
        copy.translatedText = translatedText;
        return copy;
    }

    public static TranslationUnit of(String rawJa) {
        return new TranslationUnit(rawJa, "", "");
    }

    public static TranslationUnit of(String rawJa, String ja, String ko) {
        return new TranslationUnit(rawJa, ja, ko);
    }

    public void setTranslated(String ja, String ko) {
        this.ja = ja;
        this.ko = ko;
    }

    public void setEmpty() {
        this.setTranslated("", "");
    }

    @Override
    public String getRawJa() {
        return this.rawJa;
    }

    @Override
    public void setKo(String ko) {
        this.ko = ko;
    }
}
