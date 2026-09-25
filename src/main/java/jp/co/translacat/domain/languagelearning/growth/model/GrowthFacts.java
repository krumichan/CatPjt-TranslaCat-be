package jp.co.translacat.domain.languagelearning.growth.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * null이 계약상 의미 있는 필드를 Map.of로 잃지 않도록 하는 전송용 도우미다.
 */
public final class GrowthFacts {
    private GrowthFacts() {
    }

    public static Map<String, Object> fields(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("키/값 쌍이 필요합니다.");
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    public static String nonBlankOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
