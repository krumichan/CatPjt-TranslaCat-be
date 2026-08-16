package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.entity.CustomKeyword;
import jp.co.translacat.domain.languagelearning.keyword.entity.KeywordMastery;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;
import jp.co.translacat.domain.languagelearning.keyword.entity.UserSystemKeywordSelection;
import jp.co.translacat.domain.languagelearning.keyword.model.SelectedKeywordCandidate;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordApplicationTimingPolicy;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordSelectionWeightPolicy;
import jp.co.translacat.domain.languagelearning.keyword.repository.CustomKeywordRepository;
import jp.co.translacat.domain.languagelearning.keyword.repository.KeywordMasteryRepository;
import jp.co.translacat.domain.languagelearning.keyword.repository.UserSystemKeywordSelectionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordCandidateQueryService {

    private final CustomKeywordRepository customKeywordRepository;
    private final UserSystemKeywordSelectionRepository selectionRepository;
    private final KeywordMasteryRepository masteryRepository;
    private final KeywordSelectionWeightPolicy weightPolicy;
    private final KeywordApplicationTimingPolicy applicationTimingPolicy;

    @Transactional
    public List<SelectedKeywordCandidate> findCandidates(
            Long userId,
            LocalDate learningDate
    ) {
        List<SelectedKeywordCandidate> candidates = new ArrayList<>();
        boolean applyPendingImmediately = !applicationTimingPolicy
                .hasStartedLearning(userId);

        addCustomKeywordCandidates(
                userId,
                learningDate,
                applyPendingImmediately,
                candidates
        );
        addSystemKeywordCandidates(
                userId,
                learningDate,
                applyPendingImmediately,
                candidates
        );

        return List.copyOf(candidates);
    }

    private void addCustomKeywordCandidates(
            Long userId,
            LocalDate learningDate,
            boolean applyPendingImmediately,
            List<SelectedKeywordCandidate> candidates
    ) {
        List<CustomKeyword> customKeywords = customKeywordRepository
                .findAllByUserIdOrderByIdAsc(userId);

        for (CustomKeyword keyword : customKeywords) {
            promote(
                    keyword,
                    learningDate,
                    applyPendingImmediately
            );

            if (!keyword.isActive()) {
                continue;
            }

            candidates.add(createCandidate(
                    userId,
                    "CUSTOM:" + keyword.getId(),
                    keyword.getText(),
                    KeywordSource.CUSTOM,
                    keyword.getType(),
                    keyword.getCanonicalKey(),
                    keyword.getAvailableFrom().atStartOfDay(),
                    learningDate
            ));
        }
    }

    private void addSystemKeywordCandidates(
            Long userId,
            LocalDate learningDate,
            boolean applyPendingImmediately,
            List<SelectedKeywordCandidate> candidates
    ) {
        List<UserSystemKeywordSelection> selections = selectionRepository
                .findAllByUserId(userId);

        for (UserSystemKeywordSelection selection : selections) {
            promote(
                    selection,
                    learningDate,
                    applyPendingImmediately
            );
            SystemKeyword keyword = selection.getSystemKeyword();

            if (!selection.isActive() || !keyword.isActive()) {
                continue;
            }

            candidates.add(createCandidate(
                    userId,
                    "SYSTEM:" + keyword.getId(),
                    keyword.getText(),
                    KeywordSource.SYSTEM,
                    keyword.getType(),
                    keyword.getCanonicalKey(),
                    selection.getAvailableFrom().atStartOfDay(),
                    learningDate
            ));
        }
    }

    private void promote(
            CustomKeyword keyword,
            LocalDate learningDate,
            boolean applyPendingImmediately
    ) {
        if (applyPendingImmediately) {
            keyword.promotePendingNow();
            return;
        }
        keyword.promoteIfEffective(learningDate);
    }

    private void promote(
            UserSystemKeywordSelection selection,
            LocalDate learningDate,
            boolean applyPendingImmediately
    ) {
        if (applyPendingImmediately) {
            selection.promotePendingNow();
            return;
        }
        selection.promoteIfEffective(learningDate);
    }

    private SelectedKeywordCandidate createCandidate(
            Long userId,
            String key,
            String text,
            KeywordSource source,
            KeywordType type,
            String canonicalKey,
            LocalDateTime availableFrom,
            LocalDate learningDate
    ) {
        String canonical = weightPolicy.normalizeCanonicalKey(
                canonicalKey,
                text
        );
        KeywordMastery mastery = masteryRepository
                .findByUserIdAndCanonicalKey(userId, canonical)
                .orElse(null);
        double rawWeight = weightPolicy.calculateRawWeight(
                availableFrom,
                mastery,
                learningDate
        );

        SelectedKeywordDto keyword = new SelectedKeywordDto(
                key,
                text,
                source,
                type,
                canonical,
                null
        );

        return new SelectedKeywordCandidate(
                keyword,
                type,
                rawWeight
        );
    }
}
