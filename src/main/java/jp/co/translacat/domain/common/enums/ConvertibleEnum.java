package jp.co.translacat.domain.common.enums;

public interface ConvertibleEnum {
    String name();

    default boolean matches(String source) {
        return name().equalsIgnoreCase(source.trim());
    }
}
