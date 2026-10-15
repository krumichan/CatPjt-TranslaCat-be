package jp.co.translacat.infrastructure.japanese;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractFuriganaProcessor {

    protected volatile Map<String, String> cachedDict = Map.of();

    protected abstract String analyzeWithEngine(String segment);

    public void syncCachedDict(Map<String, String> newDict) {
        // 긴 등록어부터 적용하고, 요청 중에는 비워진 사전이 보이지 않도록 한 번에 교체한다.
        Map<String, String> ordered = new LinkedHashMap<>();
        newDict.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(
                        Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder())))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        this.cachedDict = Collections.unmodifiableMap(ordered);
    }

    protected String preProcessWithDict(String rawJa) {
        Map<String, String> dictionary = cachedDict;
        if (rawJa == null || rawJa.isBlank() || dictionary.isEmpty()) return rawJa;

        // 이미 발음이 지정된 부분을 보존하고 일반 텍스트에서만 사용자 사전을 적용한다.
        String[] parts = rawJa.split("(?=<ruby)|(?<=</ruby>)");
        StringBuilder sb = new StringBuilder();

        for (String part : parts) {
            if (part.startsWith("<ruby")) {
                sb.append(part);
            } else {
                // 가장 긴 일치를 한 번만 소비한다. 삽입한 ruby를 짧은 항목으로 다시 치환하지 않는다.
                for (int offset = 0; offset < part.length();) {
                    Map.Entry<String, String> match = null;
                    for (Map.Entry<String, String> entry : dictionary.entrySet()) {
                        if (!entry.getKey().isEmpty() && part.startsWith(entry.getKey(), offset)) {
                            match = entry;
                            break;
                        }
                    }
                    if (match == null) {
                        sb.append(part.charAt(offset++));
                    } else {
                        sb.append(this.makeFineGrainedRuby(match.getKey(), match.getValue()));
                        offset += match.getKey().length();
                    }
                }
            }
        }
        return sb.toString();
    }

    public String resolveDetailedRuby(String surface, String reading) {
        if (surface == null || reading == null) return surface;
        return this.makeFineGrainedRuby(surface, reading);
    }

    public String convertToRuby(String rawJa) {
        if (rawJa == null || rawJa.isBlank()) return rawJa;

        String preProcessed = preProcessWithDict(rawJa);
        StringBuilder finalResult = new StringBuilder();
        String[] parts = preProcessed.split("(?=<ruby)|(?<=</ruby>)");

        for (String part : parts) {
            if (part.startsWith("<ruby")) {
                finalResult.append(part);
            } else {
                finalResult.append(this.analyzeWithEngine(part));
            }
        }
        return finalResult.toString();
    }

    /**
     * 공통: 문자열 내에 한자가 포함되어 있는지 확인
     */
    protected boolean containsKanji(String surface) {
        if (surface == null) return false;
        return surface.codePoints().anyMatch(this::isKanji);
    }

    protected boolean isKanji(char c) {
        return isKanji((int) c);
    }

    protected boolean isKanji(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
            return true;
        }
        return switch (codePoint) {
            case 0x3005, 0x3006, 0x30F6 -> true;
            default -> false;
        };
    }

    /**
     * 공통: 카타카나 -> 히라가나 변환
     */
    protected String convertKatakanaToHiragana(String katakana) {
        StringBuilder stringBuilder = new StringBuilder();
        for (char c : katakana.toCharArray()) {
            if (c >= 'ァ' && c <= 'ヶ') {
                stringBuilder.append((char) (c - 0x60));
            } else {
                stringBuilder.append(c);
            }
        }
        return stringBuilder.toString();
    }

    /**
     * 공통: 상세 루비 태그 생성 로직 (Sudachi와 Kuromoji가 공유)
     */
    protected String makeFineGrainedRuby(String surface, String reading) {
        if (surface.equals(reading) || reading.equals("*")) return surface;

        StringBuilder sb = new StringBuilder();
        int sIdx = 0;
        int rIdx = 0;

        while (sIdx < surface.length()) {
            char sChar = surface.charAt(sIdx);

            if (isKanji(sChar)) {
                int sStart = sIdx;
                // 연속된 한자 뭉치 찾기
                while (sIdx < surface.length() && isKanji(surface.charAt(sIdx))) {
                    sIdx++;
                }
                String kanjiPart = surface.substring(sStart, sIdx);

                // 한자 뒤에 오는 글자(오쿠리가나)를 기준으로 reading에서 위치 찾기
                if (sIdx < surface.length()) {
                    char nextChar = surface.charAt(sIdx);
                    int nextRIdx = -1;

                    // 단순히 첫 번째 'も'를 찾는 게 아니라, 남은 surface 길이와 대조
                    // 여기서는 간단하게 reading에서 현재 rIdx 이후의 가장 적절한 nextChar 위치를 탐색
                    // '突き刺さった'의 경우 'さ'가 여러 번 나오므로, surface의 남은 글자 수와 비교 로직 필요
                    for (int i = reading.length() - (surface.length() - sIdx); i >= rIdx; i--) {
                        if (i < reading.length() && isMatch(reading.charAt(i), nextChar)) {
                            nextRIdx = i;
                            break;
                        }
                    }

                    if (nextRIdx != -1) {
                        String readingPart = reading.substring(rIdx, nextRIdx);
                        sb.append("<ruby>").append(kanjiPart).append("<rt>").append(readingPart).append("</rt></ruby>");
                        rIdx = nextRIdx;
                    } else {
                        sb.append("<ruby>")
                                .append(kanjiPart)
                                .append("<rt>")
                                .append(reading.substring(rIdx))
                                .append("</rt></ruby>");
                        rIdx = reading.length();
                    }
                } else {
                    // 단어 끝이 한자인 경우
                    sb.append("<ruby>")
                            .append(kanjiPart)
                            .append("<rt>")
                            .append(reading.substring(rIdx))
                            .append("</rt></ruby>");
                    rIdx = reading.length();
                }
            } else {
                // 한자가 아닌 경우 (히라가나/가타카나/기호 등) 그대로 통과
                sb.append(sChar);
                if (rIdx < reading.length() && isMatch(sChar, reading.charAt(rIdx))) {
                    rIdx++;
                }
                sIdx++;
            }
        }
        return sb.toString();
    }

    private int findNextMatchingIndex(String reading, int start, char tail) {
        for (int i = start; i < reading.length(); i++) {
            if (isMatch(reading.charAt(i), tail)) return i;
        }
        return -1;
    }

    private boolean isMatch(char c1, char c2) {
        if (c1 == c2) return true;
        char h1 = (c1 >= 'ァ' && c1 <= 'ヶ') ? (char) (c1 - 0x60) : c1;
        char h2 = (c2 >= 'ァ' && c2 <= 'ヶ') ? (char) (c2 - 0x60) : c2;
        return h1 == h2;
    }
}
