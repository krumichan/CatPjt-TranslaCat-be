package jp.co.translacat.domain.languagelearning.keyword.dto.response;

import jp.co.translacat.domain.languagelearning.keyword.model.KeywordCandidateSnapshot;

import java.util.List;

public record EffectiveKeywordsResponseDto(List<KeywordCandidateSnapshot> candidates) {
    public EffectiveKeywordsResponseDto {
        candidates = List.copyOf(candidates);
    }
}
