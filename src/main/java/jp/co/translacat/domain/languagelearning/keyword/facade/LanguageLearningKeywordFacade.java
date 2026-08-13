package jp.co.translacat.domain.languagelearning.keyword.facade;

import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordListResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.entity.CustomKeyword;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;
import jp.co.translacat.domain.languagelearning.keyword.entity.UserSystemKeywordSelection;
import jp.co.translacat.domain.languagelearning.keyword.mapper.KeywordResponseMapper;
import jp.co.translacat.domain.languagelearning.keyword.service.CustomKeywordCommandService;
import jp.co.translacat.domain.languagelearning.keyword.service.LanguageLearningKeywordQueryService;
import jp.co.translacat.domain.languagelearning.keyword.service.SystemKeywordCommandService;
import jp.co.translacat.domain.languagelearning.keyword.service.SystemKeywordSelectionCommandService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LanguageLearningKeywordFacade {

    private final LanguageLearningKeywordQueryService keywordQueryService;
    private final CustomKeywordCommandService customKeywordCommandService;
    private final SystemKeywordCommandService systemKeywordCommandService;
    private final SystemKeywordSelectionCommandService systemKeywordSelectionCommandService;
    private final KeywordResponseMapper responseMapper;

    public KeywordListResponseDto getKeywords(Long userId) {
        return keywordQueryService.getKeywords(userId);
    }

    public KeywordResponseDto createCustomKeyword(
            Long userId,
            KeywordCreateRequestDto request
    ) {
        CustomKeyword keyword = customKeywordCommandService.create(
                userId,
                request
        );

        return responseMapper.fromCustom(keyword);
    }

    public KeywordResponseDto updateCustomKeyword(
            Long userId,
            Long keywordId,
            KeywordUpdateRequestDto request
    ) {
        CustomKeyword keyword = customKeywordCommandService.update(
                userId,
                keywordId,
                request
        );

        return responseMapper.fromCustom(keyword);
    }

    public void deleteCustomKeyword(
            Long userId,
            Long keywordId
    ) {
        customKeywordCommandService.deactivate(
                userId,
                keywordId
        );
    }

    public KeywordResponseDto updateSystemKeywordSelection(
            Long userId,
            Long keywordId,
            boolean selected
    ) {
        UserSystemKeywordSelection selection =
                systemKeywordSelectionCommandService.updateSelection(
                        userId,
                        keywordId,
                        selected
                );

        return responseMapper.fromSystem(
                selection.getSystemKeyword(),
                selection.desiredActive(),
                selection.getPendingEffectiveDate()
        );
    }

    public List<KeywordResponseDto> getSystemKeywordsForAdmin() {
        return keywordQueryService.getSystemKeywordsForAdmin();
    }

    public KeywordResponseDto createSystemKeyword(
            KeywordCreateRequestDto request
    ) {
        SystemKeyword keyword = systemKeywordCommandService.create(request);

        return responseMapper.fromSystem(
                keyword,
                false,
                null
        );
    }

    public KeywordResponseDto updateSystemKeyword(
            Long keywordId,
            KeywordUpdateRequestDto request
    ) {
        SystemKeyword keyword = systemKeywordCommandService.update(
                keywordId,
                request
        );

        return responseMapper.fromSystem(
                keyword,
                false,
                null
        );
    }
}
