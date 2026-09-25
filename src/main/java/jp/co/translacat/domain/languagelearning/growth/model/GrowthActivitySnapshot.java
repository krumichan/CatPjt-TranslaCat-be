package jp.co.translacat.domain.languagelearning.growth.model;

import jp.co.translacat.domain.languagelearning.common.enums.LearningActivityStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record GrowthActivitySnapshot(long id, LearningSource source, String referenceId, LocalDate learningDate,
                                     String title, long durationSeconds, LearningActivityStatus status,
                                     Double overallScore, Double evaluationConfidence,
                                     LocalDateTime startedAt, LocalDateTime completedAt, String metadataJson,
                                     List<GrowthMetricSnapshot> metrics) {
}
