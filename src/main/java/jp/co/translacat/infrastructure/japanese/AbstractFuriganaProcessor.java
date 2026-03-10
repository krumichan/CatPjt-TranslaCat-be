package jp.co.translacat.infrastructure.japanese;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractFuriganaProcessor {

    protected final Map<String, String> cachedDict = new ConcurrentHashMap<>();

    protected abstract String analyzeWithEngine(String segment);

    public void syncCachedDict(Map<String, String> newDict) {
        this.cachedDict.clear();
        this.cachedDict.putAll(newDict);
    }

    protected String preProcessWithDict(String rawJa) {
        if (rawJa == null || rawJa.isBlank() || cachedDict.isEmpty()) return rawJa;

        // 1. 이미 루비 태그가 달린 부분과 일반 텍스트 부분을 분리 (정규식 활용)
        // (?=<ruby)|(?<=</ruby>) 로 쪼개어서, 태그 영역과 비태그 영역 분리.
        String[] parts = rawJa.split("(?=<ruby)|(?<=</ruby>)");
        StringBuilder sb = new StringBuilder();

        for (String part : parts) {
            if (part.startsWith("<ruby")) {
                // 2. 이미 루비 태그인 부분은 건드리지 않고 그대로 유지
                sb.append(part);
            } else {
                // 3. 루비 태그가 없는 일반 텍스트 영역에서만 사전 치환 수행
                String processedPart = part;
                for (Map.Entry<String, String> entry : cachedDict.entrySet()) {
                    String surface = entry.getKey();
                    String reading = entry.getValue();

                    if (processedPart.contains(surface)) {
                        // 단순히 전체를 묶는 게 아니라 상세 로직 태움
                        // 결과: <ruby>突き<rt>つき</rt></ruby>刺<rt>さ</rt></ruby>さる
                        String fineRuby = this.makeFineGrainedRuby(surface, reading);
                        processedPart = processedPart.replace(surface, fineRuby);
                    }
                }
                sb.append(processedPart);
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
                        sb.append("<ruby>").append(kanjiPart).append("<rt>").append(reading.substring(rIdx)).append("</rt></ruby>");
                        rIdx = reading.length();
                    }
                } else {
                    // 단어 끝이 한자인 경우
                    sb.append("<ruby>").append(kanjiPart).append("<rt>").append(reading.substring(rIdx)).append("</rt></ruby>");
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
