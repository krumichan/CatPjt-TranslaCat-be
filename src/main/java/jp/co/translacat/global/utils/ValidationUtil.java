package jp.co.translacat.global.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ValidationUtil {

    /**
     * 문자열 내 일본어(히라가나, 가타카나, 한자)의 비중을 계산합니다.
     */
    public double calculateJapaneseRatio(String text) {
        if (text == null || text.isBlank()) return 0.0;

        long japaneseCharCount = text.chars()
            .filter(ValidationUtil::isJapanese)
            .count();

        return (double) japaneseCharCount / text.length();
    }

    /**
     * 단일 문자가 일본어 범위에 속하는지 확인합니다.
     */
    public boolean isJapanese(int c) {
        return (c >= 0x3040 && c <= 0x309F) || // 히라가나
               (c >= 0x30A0 && c <= 0x30FF) || // 가타카나
               (c >= 0x4E00 && c <= 0x9FFF) || // 한자
               (c >= 0xF900 && c <= 0xFAFF);   // 한자 호환
    }
}
