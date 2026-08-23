package jp.co.translacat.domain.languagelearning.listening.report.repository;

import jp.co.translacat.domain.languagelearning.listening.report.entity.ListeningEvaluationReport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ListeningEvaluationReportRepository
        extends JpaRepository<ListeningEvaluationReport, Long> {

    Optional<ListeningEvaluationReport>
    findByUserIdAndTaskResponseIdAndIdempotencyKey(
            Long userId,
            Long taskResponseId,
            String idempotencyKey
    );

    Optional<ListeningEvaluationReport> findFirstByUserIdAndTaskResponseId(
            Long userId,
            Long taskResponseId
    );
}
