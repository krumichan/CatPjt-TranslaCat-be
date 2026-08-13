package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.keyword.entity.KeywordMastery;
import jp.co.translacat.domain.languagelearning.keyword.model.SelectedKeywordCandidate;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordSelectionWeightPolicy;
import jp.co.translacat.domain.languagelearning.keyword.repository.KeywordMasteryRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordSelectionCommandService {

    private final KeywordMasteryRepository masteryRepository;
    private final UserRepository userRepository;
    private final KeywordSelectionWeightPolicy weightPolicy;

    @Transactional
    public List<SelectedKeywordDto> recordSelection(
            Long userId,
            LocalDate learningDate,
            List<SelectedKeywordCandidate> selected
    ) {
        if (selected.isEmpty()) {
            return List.of();
        }

        double maxRawWeight = selected.stream()
                .mapToDouble(candidate -> candidate.rawWeight())
                .max()
                .orElse(1.0);
        User user = userRepository.getReferenceById(userId);
        List<SelectedKeywordDto> result = new ArrayList<>();

        for (SelectedKeywordCandidate candidate : selected) {
            SelectedKeywordDto keyword = candidate.keyword();
            double normalizedWeight = weightPolicy.normalizeSelectionWeight(
                    candidate.rawWeight(),
                    maxRawWeight
            );

            result.add(withSelectionWeight(keyword, normalizedWeight));
            markSelected(
                    userId,
                    user,
                    keyword,
                    learningDate
            );
        }

        return List.copyOf(result);
    }

    private SelectedKeywordDto withSelectionWeight(
            SelectedKeywordDto keyword,
            double selectionWeight
    ) {
        return new SelectedKeywordDto(
                keyword.key(),
                keyword.text(),
                keyword.source(),
                keyword.type(),
                keyword.canonicalKey(),
                selectionWeight
        );
    }

    private void markSelected(
            Long userId,
            User user,
            SelectedKeywordDto keyword,
            LocalDate learningDate
    ) {
        String canonicalKey = weightPolicy.normalizeCanonicalKey(
                keyword.canonicalKey(),
                keyword.text()
        );
        KeywordMastery mastery = masteryRepository
                .findByUserIdAndCanonicalKey(userId, canonicalKey)
                .orElseGet(() -> masteryRepository.save(
                        KeywordMastery.create(user, canonicalKey)
                ));

        mastery.markSelected(learningDate);
    }
}
