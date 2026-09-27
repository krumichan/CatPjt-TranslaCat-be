package jp.co.translacat.domain.languagelearning.listening.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LL이 저장한 공식 평가와 진행 원본을 공통 조회 화면에 전달한다.
 */
public record ListeningReportSnapshot(List<SetFact> sets, List<EvaluationFact> evaluations,
                                      List<LearningHistoryItemResponseDto> history) {
    public record SetFact(Long setId, String learningLanguage, LocalDate learningDate,
                          int completedItemCount, int targetItemCount) {
    }

    public record EvaluationFact(Long attemptId, ListeningTaskType taskType, Double confidence,
                                 Double score, LocalDateTime evaluatedAt, List<Metric> metrics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metric(String type, Double score, double weight, double confidence) {
    }
}
