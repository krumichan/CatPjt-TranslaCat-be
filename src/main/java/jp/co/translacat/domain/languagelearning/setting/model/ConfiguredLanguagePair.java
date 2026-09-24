package jp.co.translacat.domain.languagelearning.setting.model;

/** 문제풀 보충용 언어쌍이다. 사용자 Entity나 식별자 목록을 원격으로 복제하지 않는다. */
public record ConfiguredLanguagePair(String originLanguage, String learningLanguage) { }
