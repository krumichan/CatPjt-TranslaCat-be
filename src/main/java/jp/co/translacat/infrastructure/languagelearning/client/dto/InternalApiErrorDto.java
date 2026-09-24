package jp.co.translacat.infrastructure.languagelearning.client.dto;

public record InternalApiErrorDto(
        String code,
        String message
) {
}
