package jp.co.translacat.domain.languagelearning.practice.model;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeMetricResponseDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LL 원본 결과의 홈·이력·추세용 읽기 DTO이며 답변 원문은 포함하지 않는다.
 */
public record PracticeReportSnapshot(List<SetFact> sets) {
    public record SetFact(Long setId, LocalDate learningDate, PracticeDomain domain, String mode,
                          PracticeSetStatus status, int questionCount, int answeredCount, Double officialScore,
                          LocalDateTime startedAt, LocalDateTime completedAt, List<PracticeMetricResponseDto> metrics) {
    }
}
