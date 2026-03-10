package jp.co.translacat.domain.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

public enum PlatformCode implements ConvertibleEnum {
    SYOSETU,
    KAKUYOMU;

    @JsonCreator
    public static PlatformCode fromString(String source) {
        if (source == null) return null;
        return Arrays.stream(PlatformCode.values())
            .filter(code -> code.matches(source))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown platform: " + source));
    }
}
