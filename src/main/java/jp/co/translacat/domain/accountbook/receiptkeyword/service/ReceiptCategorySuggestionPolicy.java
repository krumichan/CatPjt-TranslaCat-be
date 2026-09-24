package jp.co.translacat.domain.accountbook.receiptkeyword.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Keeps category suggestions stable without pre-creating categories in every account book. */
public final class ReceiptCategorySuggestionPolicy {
    private ReceiptCategorySuggestionPolicy() {}

    public static final List<String> DEFAULT_CATEGORIES = List.of(
            "식비", "교통비", "생활", "쇼핑", "의료", "주거", "기타");

    public static Resolution resolve(
            String proposedName,
            String proposedReason,
            List<String> existingCategories,
            List<String> defaultCategories) {
        Map<String, String> existing = canonical(existingCategories);
        Map<String, String> defaults = canonical(defaultCategories);
        String name = cleanName(proposedName);
        String reason = cleanReason(proposedReason);
        List<String> warnings = new ArrayList<>();
        if (name == null) {
            warnings.add("CATEGORY_FALLBACK_USED");
            return new Resolution("기타", "FALLBACK",
                    reason == null ? "분류 근거가 부족함" : reason, warnings);
        }
        String key = key(name);
        if (existing.containsKey(key))
            return new Resolution(existing.get(key), "EXISTING", reason, warnings);
        if (defaults.containsKey(key))
            return new Resolution(defaults.get(key), "DEFAULT", reason, warnings);
        warnings.add("CATEGORY_NEW_SUGGESTION");
        return new Resolution(name, "NEW", reason, warnings);
    }

    public static List<String> mergeOptions(List<String> existingCategories) {
        Map<String, String> merged = canonical(existingCategories);
        canonical(DEFAULT_CATEGORIES).forEach(merged::putIfAbsent);
        return List.copyOf(merged.values());
    }

    private static Map<String, String> canonical(List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        if (values == null) return result;
        for (String value : values) {
            String clean = cleanName(value);
            if (clean != null) result.putIfAbsent(key(clean), clean);
        }
        return result;
    }

    private static String cleanName(String value) {
        if (value == null) return null;
        String name = value.trim();
        if (name.isEmpty() || name.length() > 50
                || name.codePoints().anyMatch(Character::isISOControl)
                || name.codePoints().noneMatch(Character::isLetterOrDigit)) return null;
        return name;
    }

    private static String cleanReason(String value) {
        if (value == null) return null;
        String reason = value.trim();
        if (reason.isEmpty() || reason.codePoints().anyMatch(Character::isISOControl)) return null;
        return reason.substring(0, Math.min(160, reason.length()));
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    public record Resolution(String name, String source, String reason, List<String> warnings) {}
}
