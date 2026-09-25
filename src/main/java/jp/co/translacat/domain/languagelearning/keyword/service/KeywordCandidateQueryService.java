package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.keyword.model.SelectedKeywordCandidate;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordSelectionWeightPolicy;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordCatalogGateway;
import jp.co.translacat.domain.languagelearning.profile.dto.response.KeywordMasteryResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 후보는 LL Catalog, 숙련도/선택 횟수도 LL에서 읽는다. 기본 키워드를 임의로 배정하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class KeywordCandidateQueryService {
    private final KeywordCatalogGateway catalog;
    private final GrowthReadGateway growth;
    private final KeywordSelectionWeightPolicy weightPolicy;

    public List<SelectedKeywordCandidate> findCandidates(Long userId, LocalDate learningDate) {
        var candidates = catalog.candidates(userId, learningDate);
        if (candidates.isEmpty()) return List.of();
        var keys = candidates.stream()
                .map(v -> weightPolicy.normalizeCanonicalKey(v.canonicalKey(), v.text()))
                .distinct()
                .toList();
        Map<String, KeywordMasteryResponseDto> masteries = new HashMap<>();
        for (int i = 0; i < keys.size(); i += 500)
            growth.snapshot(userId, keys.subList(i, Math.min(keys.size(), i + 500)))
                    .masteries()
                    .forEach(value -> masteries.put(value.canonicalKey(), value));
        return candidates.stream().map(candidate -> {
            String canonical = weightPolicy.normalizeCanonicalKey(candidate.canonicalKey(), candidate.text());
            double weight =
                    weightPolicy.calculateRawWeight(candidate.availableFrom().atStartOfDay(), masteries.get(canonical),
                            learningDate);
            return new SelectedKeywordCandidate(
                    new SelectedKeywordDto(candidate.key(), candidate.text(), candidate.source(), candidate.type(),
                            canonical, null), candidate.type(), weight);
        }).toList();
    }
}
