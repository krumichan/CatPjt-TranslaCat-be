package jp.co.translacat.global.utils;

import java.util.function.Supplier;

public final class ValueUtil {

    private ValueUtil() {
    }

    public static <T> T defaultIfNull(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    public static <T> T defaultIfNull(T value, Supplier<T> defaultValueSupplier) {
        return value == null ? defaultValueSupplier.get() : value;
    }

    public static String normalizeContent(String content) {
        return content.trim();
    }
}