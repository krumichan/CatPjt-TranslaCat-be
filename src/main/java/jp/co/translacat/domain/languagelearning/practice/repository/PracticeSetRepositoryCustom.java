package jp.co.translacat.domain.languagelearning.practice.repository;

import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface PracticeSetRepositoryCustom {
    List<PracticeSet> findDueByGenerationStatus(
            PracticeGenerationStatus status,
            LocalDateTime now,
            int limit
    );
}
