package jp.co.translacat.domain.languagelearning.keyword.port;

import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordListResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordCandidateSnapshot;

import java.time.LocalDate;
import java.util.List;

public interface KeywordCatalogGateway {
    KeywordListResponseDto getKeywords(Long userId, String uiLocale);

    KeywordResponseDto createCustom(Long userId, KeywordCreateRequestDto request);

    KeywordResponseDto updateCustom(Long userId, Long keywordId, KeywordUpdateRequestDto request);

    void deleteCustom(Long userId, Long keywordId);

    KeywordResponseDto selectSystem(Long userId, Long keywordId, boolean selected);

    List<KeywordResponseDto> getSystemKeywords(Long adminUserId);

    KeywordResponseDto createSystem(Long adminUserId, KeywordCreateRequestDto request);

    KeywordResponseDto updateSystem(Long adminUserId, Long keywordId, KeywordUpdateRequestDto request);

    List<KeywordCandidateSnapshot> candidates(Long userId, LocalDate learningDate);
}
