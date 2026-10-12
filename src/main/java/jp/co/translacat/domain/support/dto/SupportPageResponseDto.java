package jp.co.translacat.domain.support.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record SupportPageResponseDto<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <S, T> SupportPageResponseDto<T> from(Page<S> result, Function<S, T> mapper) {
        return new SupportPageResponseDto<>(
                result.getContent().stream().map(mapper).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
