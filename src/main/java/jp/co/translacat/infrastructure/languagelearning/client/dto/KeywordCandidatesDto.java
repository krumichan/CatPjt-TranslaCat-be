package jp.co.translacat.infrastructure.languagelearning.client.dto;

import jp.co.translacat.domain.languagelearning.keyword.model.KeywordCandidateSnapshot;

import java.util.List;

public record KeywordCandidatesDto(List<KeywordCandidateSnapshot> candidates) {
}
