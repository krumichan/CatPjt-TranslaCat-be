package jp.co.translacat.global.converter;

import jp.co.translacat.domain.common.enums.ConvertibleEnum;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import java.util.stream.Stream;

public class StringToConvertibleEnumConverterFactory
        implements ConverterFactory <String, ConvertibleEnum> {

    @NotNull
    @Override
    public <T extends ConvertibleEnum> Converter<String, T> getConverter(@NotNull Class<T> targetType) {
        return source -> {
            return Stream.of(targetType.getEnumConstants())
                .filter(e -> e.matches(source))
                .findFirst()
                .orElseThrow(()
                    -> new IllegalArgumentException("지원하지 않는 코드입니다: " + source));
       };
    }
}
