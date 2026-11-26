package jp.co.translacat.domain.documenttranslation.translation;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 명시적 일본어→영어 문서 번역에서 원문에 근거한 보정과 검사를 제공한다. */
final class JapaneseDocumentFidelity {
    private static final Pattern LOCATIVE_GUIDANCE = Pattern.compile("(?:の手前|の前|の近く|の付近)で(?:案内|誘導)");
    private static final Pattern MOVEMENT_CUE = Pattern.compile(
            "待[つちっ]|待機|お待ち|止ま|停止|並[ぶびん]|列(?:に|を)|整列|留ま|とどま|滞留|留まり");
    private static final Pattern ADDED_ACTION = Pattern.compile(
            "(?i)(\\b(?:guide|direct|lead|escort) +[a-z][a-z '-]{0,80}?)"
                    + " +to +(?:wait|stop|stand +still|queue(?: +up)?|line +up) +"
                    + "(?=(?:before|in +front +of|near|at|by|outside)\\b)");
    private static final Pattern FACILITY_SOURCE = Pattern.compile(
            "([東西南北])(?:側の?)?(通路|入口|出口|階段)");
    private static final Map<Character, String> DIRECTIONS = Map.of('東', "east", '西', "west", '南', "south", '北', "north");
    private static final Map<String, String> FACILITIES = Map.of(
            "通路", "corridor|passageway|passage|hallway",
            "入口", "entrance|entryway|entry",
            "出口", "exit",
            "階段", "stairs|stairway|staircase");
    private static final Pattern HANGUL = Pattern.compile("\\p{IsHangul}+");
    private static final String INFORMATION_SIGN_SOURCE = "案内表示";
    private static final Pattern LEAKED_SIGN_MODIFIER = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])안내(?= +(?:signs?|signage|displays?)\\b)");
    private static final Pattern QUOTED = Pattern.compile("「([^」\\r\\n]+)」|『([^』\\r\\n]+)』|“([^”\\r\\n]+)”"
            + "|\"([^\"\\r\\n]+)\"");
    private static final Pattern SINGLE_QUOTED = Pattern.compile(
            "‘[^’\\r\\n]+’|(?<![\\p{L}\\p{N}])'[^'\\r\\n]+'(?![\\p{L}\\p{N}])");
    private static final Pattern LATIN_LABEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._&+/-]{0,79}");
    private static final Pattern CLOSING_KEY_OWNER = Pattern.compile(
            "閉館時には[、,]?([\\p{IsHan}]+)の鍵を戻し、責任者に報告します[。.]?");
    private static final Map<String, String> KEY_OWNERS = Map.of(
            "案内所", "information (?:desk|counter)",
            "南通路", "south (?:corridor|passageway|passage|hallway)",
            "資料室", "(?:reference|document|materials|archive|records) room",
            "東入口", "east entrance",
            "展示室", "exhibition room",
            "北通路", "north (?:corridor|passage)",
            "受付", "reception(?: desk)?",
            "西入口", "west entrance",
            "休憩室", "break room",
            "会議室", "(?:meeting|conference) room");
    private static final Pattern BREAK_ROOM_SOURCE = Pattern.compile("休憩室");
    private static final Pattern TOILET_SOURCE = Pattern.compile(
            "トイレ|便所|お手洗い|化粧室|洗面所|(?i:\\b(?:toilet|rest ?room|bathroom)\\b)");
    private static final Pattern MISREAD_BREAK_ROOM = Pattern.compile("(?i)\\brest room\\b");
    private static final Pattern ALREADY_BREAK_ROOM = Pattern.compile(
            "(?i)\\b(?:break room|lounge|staff room|rest area|break area)\\b");

    private JapaneseDocumentFidelity() {
    }

    // 인용되지 않은 한 조각에서 원문과 확실히 대조할 수 있는 영어 표현만 보정한다.
    static String correctSinglePiece(String source, String translated) {
        if (hasQuote(source) || hasQuote(translated)) {
            return translated;
        }
        String result = correctInformationSign(source, translated);
        result = correctGuidanceAndDirection(source, result);
        result = correctKeyOwner(source, result);
        return correctBreakRoom(source, result);
    }

    // 일본어 안내 표시에 대응하는 영어 표지 명사가 있을 때만 새로 섞인 한글 수식어를 영어로 보정한다.
    private static String correctInformationSign(String source, String translated) {
        int start = source.indexOf(INFORMATION_SIGN_SOURCE);
        if (start < 0 || start != source.lastIndexOf(INFORMATION_SIGN_SOURCE) || HANGUL.matcher(source).find()) {
            return translated;
        }
        Matcher leaked = LEAKED_SIGN_MODIFIER.matcher(translated);
        if (!leaked.find()) {
            return translated;
        }
        int matchStart = leaked.start();
        int matchEnd = leaked.end();
        if (leaked.find()) {
            return translated;
        }
        String modifier = matchStart == 0 ? "Information" : "information";
        return translated.substring(0, matchStart) + modifier + translated.substring(matchEnd);
    }

    // 원문과 번역문의 큰따옴표·작은따옴표 안에 있는 저자 표기를 보정에서 보호한다.
    private static boolean hasQuote(String text) {
        return QUOTED.matcher(text).find() || SINGLE_QUOTED.matcher(text).find();
    }

    // 안내 자체와 장소를 보존하며 추가된 행동 또는 서로 뒤바뀐 방향을 확인한다.
    private static String correctGuidanceAndDirection(String source, String translated) {
        String result = translated;

        // 안내 자체와 장소를 보존하면서 영어가 부가한 목적어의 행동만 제거한다.
        if (LOCATIVE_GUIDANCE.matcher(source).find() && !MOVEMENT_CUE.matcher(source).find()) {
            result = ADDED_ACTION.matcher(result).replaceAll("$1 ");
        }

        // 원문에서 방향과 시설 유형이 하나로 특정되는 경우만 같은 영어 유형의 반대 방향을 보정한다.
        Matcher licensed = FACILITY_SOURCE.matcher(source);
        if (!licensed.find()) {
            return result;
        }
        char direction = licensed.group(1).charAt(0);
        String nouns = FACILITIES.get(licensed.group(2));
        if (licensed.find() || DIRECTIONS.keySet().stream()
                .anyMatch(other -> other != direction && source.indexOf(other) >= 0)
                || source.contains("反対") || source.contains("向かい")) {
            return result;
        }
        String expected = DIRECTIONS.get(direction);
        String opposite = switch (direction) {
            case '東' -> "west";
            case '西' -> "east";
            case '南' -> "north";
            default -> "south";
        };
        if (source.toLowerCase(Locale.ROOT).contains(opposite)
                || Pattern.compile("(?i)\\b" + expected + " +(?:" + nouns + ")\\b").matcher(result).find()) {
            return result;
        }
        Matcher reversed = Pattern.compile("(?i)\\b(" + opposite + ")(?= +(?:" + nouns + ")\\b)").matcher(result);
        if (!reversed.find()) {
            return result;
        }
        String card = reversed.group(1);
        String replacement = Character.isUpperCase(card.charAt(0))
                ? Character.toUpperCase(expected.charAt(0)) + expected.substring(1) : expected;
        return reversed.replaceFirst(replacement);
    }

    // 반납 장소가 없는 폐관 절차에서 같은 장소를 가리키는 영어 열쇠 표현만 소속으로 돌린다.
    private static String correctKeyOwner(String source, String translated) {
        Matcher closing = CLOSING_KEY_OWNER.matcher(source);
        if (!closing.matches()) {
            return translated;
        }
        String owner = KEY_OWNERS.get(closing.group(1));
        if (owner == null) {
            return translated;
        }
        Matcher phrase = Pattern.compile("(?i)\\b(return) the key to (?:the )?(" + owner
                + ")(?=,? and report\\b|[.!?]|\\z)").matcher(translated);
        if (!phrase.find()) {
            return translated;
        }
        String replacement = phrase.group(1) + " the " + phrase.group(2) + " key";
        return phrase.find() ? translated : phrase.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    // 화장실과 휴게실을 함께 가리키거나 대응 위치가 여러 곳이면 용어를 치환하지 않는다.
    private static String correctBreakRoom(String source, String translated) {
        Matcher room = BREAK_ROOM_SOURCE.matcher(source);
        if (!room.find() || room.find() || TOILET_SOURCE.matcher(source).find()
                || ALREADY_BREAK_ROOM.matcher(translated).find()) {
            return translated;
        }
        Matcher phrase = MISREAD_BREAK_ROOM.matcher(translated);
        if (!phrase.find()) {
            return translated;
        }
        String original = phrase.group();
        String rest = original.substring(0, 4);
        String replacement = switch (rest) {
            case "REST" -> "BREAK";
            case "Rest" -> "Break";
            default -> "break";
        };
        return phrase.find() ? translated : phrase.replaceFirst(replacement + original.substring(4));
    }

    // 번역문 한글은 원문 한글의 부분집합이어야 한다.
    static boolean preservesHangul(String source, String translated) {
        String canonicalSource = Normalizer.normalize(source, Normalizer.Form.NFC);
        String canonicalTarget = Normalizer.normalize(translated, Normalizer.Form.NFC);
        var allowed = new HashMap<String, Integer>();
        Matcher authored = HANGUL.matcher(canonicalSource);
        while (authored.find()) {
            allowed.merge(authored.group(), 1, Integer::sum);
        }

        Matcher observed = HANGUL.matcher(canonicalTarget);
        while (observed.find()) {
            Integer remaining = allowed.get(observed.group());
            if (remaining == null || remaining == 0) {
                return false;
            }
            allowed.put(observed.group(), remaining - 1);
        }
        return true;
    }

    // 인용된 한글·영문 표지를 번역된 문장 안에 정확한 원문 표기로 남겼는지 확인한다.
    static boolean preservesQuotedLabels(String source, String translated) {
        var quotedLabels = new HashMap<String, Integer>();
        Matcher quote = QUOTED.matcher(source);
        while (quote.find()) {
            for (int group = 1; group <= quote.groupCount(); group++) {
                String label = quote.group(group);
                if (label != null && (HANGUL.matcher(label).find() || LATIN_LABEL.matcher(label).matches())
                        && quotedLabels.merge(label, 1, Integer::sum) > count(translated, label)) {
                    return false;
                }
            }
        }
        return true;
    }

    // 더 긴 단어의 일부를 같은 표지로 세지 않고 완전한 표기의 출현 수를 센다.
    private static int count(String text, String literal) {
        int matches = 0;
        for (int position = text.indexOf(literal); position >= 0;
                position = text.indexOf(literal, position + literal.length())) {
            int end = position + literal.length();
            boolean startsWord = position > 0 && Character.isLetterOrDigit(text.codePointBefore(position));
            boolean endsWord = end < text.length() && Character.isLetterOrDigit(text.codePointAt(end));
            if (!startsWord && !endsWord) {
                matches++;
            }
        }
        return matches;
    }
}
