package jp.co.translacat.domain.languagelearning.keyword.facade;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.keyword.model.SelectedKeywordCandidate;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordSelectionPolicy;
import jp.co.translacat.domain.languagelearning.keyword.service.KeywordCandidateQueryService;
import jp.co.translacat.domain.languagelearning.keyword.service.KeywordSelectionCommandService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class KeywordSelectionFacade {

    private final KeywordCandidateQueryService candidateQueryService;
    private final KeywordSelectionCommandService selectionCommandService;
    private final KeywordSelectionPolicy selectionPolicy;

    public List<SelectedKeywordDto> selectForDailySet(
            Long userId,
            LocalDate learningDate,
            LanguageLearningAdminSetting adminSetting
    ) {
        int maxCount = adminSetting.getDailyKeywordMaxCount();
        if (maxCount <= 0) {
            return List.of();
        }

        List<SelectedKeywordCandidate> candidates =
                candidateQueryService.findCandidates(
                        userId,
                        learningDate
                );
        if (candidates.isEmpty()) {
            return List.of();
        }

        Random random = new Random(Objects.hash(userId, learningDate));
        List<SelectedKeywordCandidate> selected = selectionPolicy.select(
                candidates,
                maxCount,
                random
        );

        return selectionCommandService.recordSelection(
                userId,
                learningDate,
                selected
        );
    }
}
