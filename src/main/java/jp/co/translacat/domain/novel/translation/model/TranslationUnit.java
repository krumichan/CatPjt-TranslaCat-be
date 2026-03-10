package jp.co.translacat.domain.novel.translation.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TranslationUnit implements Translatable {
    private String rawJa;
    private String ja;
    private String ko;

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