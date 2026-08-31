package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionCandidate;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionCandidateStatus;
import jp.co.translacat.domain.languagelearning.level.pool.repository.LevelTestQuestionCandidateRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LevelTestQuestionCandidateQueryService {

    private final LevelTestQuestionCandidateRepository repository;

    @Transactional(readOnly = true)
    public Optional<LevelTestQuestionCandidate> findAvailable(
            Long sessionId,
            int questionNumber,
            int complexityBand
    ) {
        return repository
                .findBySessionIdAndQuestionNumberAndComplexityBandAndStatus(
                        sessionId,
                        questionNumber,
                        complexityBand,
                        LevelTestQuestionCandidateStatus.AVAILABLE
                );
    }

    @Transactional(readOnly = true)
    public Optional<LevelTestQuestionCandidate> findAny(
            Long sessionId,
            int questionNumber,
            int complexityBand
    ) {
        return repository.findBySessionIdAndQuestionNumberAndComplexityBand(
                sessionId,
                questionNumber,
                complexityBand
        );
    }

    /**
     * Polling / cross-thread hand-off 전용 조회.
     *
     * <p>Level Test prefetch worker가 candidate를 GENERATING -> AVAILABLE로
     * 전환하는 동안 HTTP 요청의 Open-EntityManager persistence context에는
     * 같은 candidate Entity의 이전 상태가 남아 있을 수 있다. Entity를 그대로
     * 반환하면 SELECT가 AVAILABLE row를 찾았어도 1차 캐시의 stale Entity에서
     * poolQuestionId=null을 읽을 수 있으므로, closed projection으로 필요한
     * 컬럼만 직접 읽어 immutable snapshot으로 반환한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<CandidateSnapshot> findAvailableSnapshot(
            Long sessionId,
            int questionNumber,
            int complexityBand
    ) {
        return repository
                .findSnapshotBySessionIdAndQuestionNumberAndComplexityBandAndStatus(
                        sessionId,
                        questionNumber,
                        complexityBand,
                        LevelTestQuestionCandidateStatus.AVAILABLE
                )
                .map(this::snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<CandidateSnapshot> findAnySnapshot(
            Long sessionId,
            int questionNumber,
            int complexityBand
    ) {
        return repository
                .findSnapshotBySessionIdAndQuestionNumberAndComplexityBand(
                        sessionId,
                        questionNumber,
                        complexityBand
                )
                .map(this::snapshot);
    }

    private CandidateSnapshot snapshot(
            LevelTestQuestionCandidateRepository.CandidateSnapshotProjection value
    ) {
        return new CandidateSnapshot(
                value.getId(),
                value.getPoolQuestionId(),
                value.getStatus()
        );
    }

    public record CandidateSnapshot(
            Long id,
            Long poolQuestionId,
            LevelTestQuestionCandidateStatus status
    ) {
    }
}
