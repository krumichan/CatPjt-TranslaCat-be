package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.keyword.entity.KeywordMastery;
import jp.co.translacat.domain.languagelearning.keyword.model.SelectedKeywordCandidate;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordSelectionWeightPolicy;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordCatalogGateway;
import jp.co.translacat.domain.languagelearning.keyword.repository.KeywordMasteryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

/** LL은 활성 후보만 반환한다. 평가/선택 횟수와 가중치 계산은 기존 Core 트랜잭션을 유지한다. */
@Service
@RequiredArgsConstructor
public class KeywordCandidateQueryService {
    private final KeywordCatalogGateway catalog;
    private final KeywordMasteryRepository masteryRepository;
    private final KeywordSelectionWeightPolicy weightPolicy;

    @Transactional
    public List<SelectedKeywordCandidate> findCandidates(Long userId, LocalDate learningDate) {
        return catalog.candidates(userId, learningDate).stream().map(candidate -> {
            String canonical = weightPolicy.normalizeCanonicalKey(candidate.canonicalKey(), candidate.text());
            KeywordMastery mastery = masteryRepository.findByUserIdAndCanonicalKey(userId, canonical).orElse(null);
            double rawWeight = weightPolicy.calculateRawWeight(candidate.availableFrom().atStartOfDay(), mastery, learningDate);
            SelectedKeywordDto keyword = new SelectedKeywordDto(candidate.key(), candidate.text(), candidate.source(), candidate.type(), canonical, null);
            return new SelectedKeywordCandidate(keyword, candidate.type(), rawWeight);
        }).toList();
    }
}
