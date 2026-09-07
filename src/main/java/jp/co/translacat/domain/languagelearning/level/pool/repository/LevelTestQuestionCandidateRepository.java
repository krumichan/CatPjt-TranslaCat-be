package jp.co.translacat.domain.languagelearning.level.pool.repository;

import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionCandidate;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionCandidateStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LevelTestQuestionCandidateRepository
        extends JpaRepository<LevelTestQuestionCandidate, Long> {

    Optional<LevelTestQuestionCandidate>
    findBySessionIdAndQuestionNumberAndComplexityBand(
            Long sessionId,
            int questionNumber,
            int complexityBand
    );


    Optional<CandidateSnapshotProjection>
    findSnapshotBySessionIdAndQuestionNumberAndComplexityBand(
            Long sessionId,
            int questionNumber,
            int complexityBand
    );

    Optional<CandidateSnapshotProjection>
    findSnapshotBySessionIdAndQuestionNumberAndComplexityBandAndStatus(
            Long sessionId,
            int questionNumber,
            int complexityBand,
            LevelTestQuestionCandidateStatus status
    );

    List<LevelTestQuestionCandidate>
    findAllBySessionIdAndQuestionNumberAndStatus(
            Long sessionId,
            int questionNumber,
            LevelTestQuestionCandidateStatus status
    );

    interface CandidateSnapshotProjection {

        Long getId();

        Long getPoolQuestionId();

        LevelTestQuestionCandidateStatus getStatus();
    }
}
