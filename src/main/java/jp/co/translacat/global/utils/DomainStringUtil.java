package jp.co.translacat.global.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class DomainStringUtil {

    public static String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    public static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}