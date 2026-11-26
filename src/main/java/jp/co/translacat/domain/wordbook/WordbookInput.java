package jp.co.translacat.domain.wordbook;

import jp.co.translacat.domain.wordbook.WordbookTypes.Draft;
import jp.co.translacat.domain.wordbook.WordbookTypes.LanguagePair;
import jp.co.translacat.domain.wordbook.WordbookTypes.SourceKind;
import org.springframework.http.HttpStatus;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** 단어장 입력의 유니코드 범위와 재전송·표현 비교 기준을 계산한다. */
final class WordbookInput {
    private static final Set<LanguagePair> LANGUAGE_PAIRS = Set.of(
            new LanguagePair("ja", "ko"), new LanguagePair("ko", "ja"), new LanguagePair("en", "ko"));
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern STRONG_VERSION = Pattern.compile("\"[1-9][0-9]{0,18}\"");
    private static final Pattern PAGE_SIZE = Pattern.compile("[0-9]{1,2}");

    private WordbookInput() {
    }

    /** 지정된 원어·목표어를 검증한다. 필터에서만 둘의 생략을 허용한다. */
    static LanguagePair languages(String sourceLanguage, String targetLanguage, boolean allowOmission) {
        if (allowOmission && sourceLanguage == null && targetLanguage == null) {
            return null;
        }
        var languages = new LanguagePair(sourceLanguage, targetLanguage);
        if (!LANGUAGE_PAIRS.contains(languages)) {
            throw WordbookProblem.invalid();
        }
        return languages;
    }

    /** 화면에 나타날 텍스트는 NFC와 양끝 공백만 조정한다. */
    static Draft draft(String sourceLanguage, String targetLanguage, String sourceText, String targetText,
            String context, String sourceLabel) {
        return new Draft(
                languages(sourceLanguage, targetLanguage, false),
                text(sourceText, 200, false),
                text(targetText, 500, false),
                text(context, 500, true),
                text(sourceLabel, 80, true));
    }

    /** 생성 시 저장 당시 출처의 공개 열거값만 받는다. */
    static SourceKind sourceKind(String value) {
        try {
            return SourceKind.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw WordbookProblem.invalid();
        }
    }

    /** 비교에는 화면 표시와 달리 연속 유니코드 공백을 같은 문자로 취급한다. */
    static String pairText(String value) {
        return WHITESPACE.matcher(value).replaceAll(" ");
    }

    /** 언어 방향과 표현은 저장소의 유일 키와 같은 범위에서 비교한다. */
    static String pairHash(Draft draft) {
        return digest(pairText(draft.sourceText()), pairText(draft.targetText()));
    }

    /** 멱등성은 실제 저장되는 내용과 출처를 포함하는 전체 요청에 묶는다. */
    static String requestHash(Draft draft, SourceKind kind) {
        return digest(draft.languages().sourceLanguage(), draft.languages().targetLanguage(), draft.sourceText(),
                draft.targetText(), draft.context(), draft.sourceLabel(), kind.name());
    }

    /** HTTP 요청은 축약 형태를 제외하고 표준 UUID 문자열로 받는다. */
    static UUID uuid(String value) {
        if (value == null || value.length() != 36) {
            throw WordbookProblem.invalid();
        }
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw WordbookProblem.invalid();
            }
            return id;
        } catch (IllegalArgumentException invalid) {
            throw WordbookProblem.invalid();
        }
    }

    /** 버전을 수정 또는 삭제하는 요청은 현재 강한 ETag 한 개를 제공해야 한다. */
    static long version(String header) {
        if (header == null) {
            throw new WordbookProblem(HttpStatus.PRECONDITION_REQUIRED, "VERSION_REQUIRED");
        }
        if (!STRONG_VERSION.matcher(header).matches()) {
            throw WordbookProblem.invalid();
        }
        try {
            return Long.parseLong(header, 1, header.length() - 1, 10);
        } catch (NumberFormatException invalid) {
            throw WordbookProblem.invalid();
        }
    }

    /** 비어 있는 값과 상한 밖의 페이지 크기를 거부한다. */
    static int limit(String value) {
        if (value == null) {
            return 20;
        }
        if (!PAGE_SIZE.matcher(value).matches()) {
            throw WordbookProblem.invalid();
        }
        int limit = Integer.parseInt(value);
        if (limit < 1 || limit > 50) {
            throw WordbookProblem.invalid();
        }
        return limit;
    }

    // NFC 처리 전에 고립 서로게이트를 거부해 잘못된 입력이 해시 과정에서 같은 값으로 바뀌지 않게 한다.
    private static String text(String value, int maximum, boolean nullable) {
        if (value == null && nullable) {
            return null;
        }
        if (value == null || value.codePoints().anyMatch(code -> code >= 0xD800 && code <= 0xDFFF)) {
            throw WordbookProblem.invalid();
        }

        // Java의 UTF-16 단위가 아니라 API가 사용하는 유니코드 코드 포인트로 길이를 센다.
        String normalized = EDGE_WHITESPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFC)).replaceAll("");
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maximum) {
            throw WordbookProblem.invalid();
        }
        return normalized;
    }

    // 길이 접두사를 사용해 필드 경계와 null을 보존한다.
    private static String digest(String... fields) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required", unavailable);
        }

        for (String field : fields) {
            if (field == null) {
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            } else {
                byte[] encoded = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
                digest.update(encoded);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
