package jp.co.translacat.domain.novel.novel.dto;

import jp.co.translacat.domain.common.dto.PageNumberResponseDto;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;

import java.util.List;

public record NovelPageResponseDto(
        PageNumberResponseDto pageInfo,
        TranslationUnit title,
        TranslationUnit author,
        TranslationUnit synopsis,
        List<NovelResponseDto> episodes) {}
