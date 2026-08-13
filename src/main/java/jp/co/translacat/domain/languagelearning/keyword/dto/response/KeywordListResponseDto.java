package jp.co.translacat.domain.languagelearning.keyword.dto.response;

import java.util.List;

public record KeywordListResponseDto(
        List<KeywordResponseDto> systemKeywords,
        List<KeywordResponseDto> customKeywords
) {
}
