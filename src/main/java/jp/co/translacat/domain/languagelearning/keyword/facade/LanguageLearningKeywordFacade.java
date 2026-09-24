package jp.co.translacat.domain.languagelearning.keyword.facade;

import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordListResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordCatalogGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** 외부 FE 계약은 유지한다. 키워드 Entity/Repository를 조회하거나 저장하지 않는다. */
@Service
@RequiredArgsConstructor
public class LanguageLearningKeywordFacade {
    private final KeywordCatalogGateway keywords;

    public KeywordListResponseDto getKeywords(Long userId, String uiLocale) { return keywords.getKeywords(userId, uiLocale); }
    public KeywordResponseDto createCustomKeyword(Long userId, KeywordCreateRequestDto request) { return keywords.createCustom(userId, request); }
    public KeywordResponseDto updateCustomKeyword(Long userId, Long keywordId, KeywordUpdateRequestDto request) { return keywords.updateCustom(userId, keywordId, request); }
    public void deleteCustomKeyword(Long userId, Long keywordId) { keywords.deleteCustom(userId, keywordId); }
    public KeywordResponseDto updateSystemKeywordSelection(Long userId, Long keywordId, boolean selected) { return keywords.selectSystem(userId, keywordId, selected); }
    public List<KeywordResponseDto> getSystemKeywordsForAdmin(Long adminUserId) { return keywords.getSystemKeywords(adminUserId); }
    public KeywordResponseDto createSystemKeyword(Long adminUserId, KeywordCreateRequestDto request) { return keywords.createSystem(adminUserId, request); }
    public KeywordResponseDto updateSystemKeyword(Long adminUserId, Long keywordId, KeywordUpdateRequestDto request) { return keywords.updateSystem(adminUserId, keywordId, request); }
}
