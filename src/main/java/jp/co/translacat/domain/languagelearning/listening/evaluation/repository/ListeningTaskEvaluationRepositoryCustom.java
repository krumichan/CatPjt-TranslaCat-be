package jp.co.translacat.domain.languagelearning.listening.evaluation.repository;

import jp.co.translacat.domain.languagelearning.listening.evaluation.entity.ListeningTaskEvaluation;

import java.time.LocalDateTime;
import java.util.List;

public interface ListeningTaskEvaluationRepositoryCustom {

    List<ListeningTaskEvaluation> findOfficialTrendSource(
            Long userId,
            String learningLanguage,
            LocalDateTime from,
            LocalDateTime to
    );
}
