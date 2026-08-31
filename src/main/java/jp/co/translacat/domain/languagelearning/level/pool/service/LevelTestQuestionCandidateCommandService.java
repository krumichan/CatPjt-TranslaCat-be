package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionCandidate;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionCandidateStatus;
import jp.co.translacat.domain.languagelearning.level.pool.repository.LevelTestQuestionCandidateRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LevelTestQuestionCandidateCommandService {

    private final LevelTestQuestionCandidateRepository repository;
    private final LevelTestQuestionCandidateQueryService queryService;
    private final LevelTestQuestionCandidateInsertService insertService;

    public Reservation reserve(
            Long sessionId,
            int questionNumber,
            int complexityBand
    ) {
        LevelTestQuestionCandidate existing = queryService.findAny(
                sessionId,
                questionNumber,
                complexityBand
        ).orElse(null);
        if (existing != null) {
            return new Reservation(existing.getId(), false);
        }

        try {
            LevelTestQuestionCandidate created = insertService.insertReservation(
                    sessionId,
                    questionNumber,
                    complexityBand
            );
            return new Reservation(created.getId(), true);
        } catch (DataIntegrityViolationException exception) {
            return queryService.findAny(
                    sessionId,
                    questionNumber,
                    complexityBand
            )
                    .map(value -> new Reservation(value.getId(), false))
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional
    public void markAvailable(Long candidateId, Long poolQuestionId) {
        repository.findById(candidateId)
                .ifPresent(value -> value.markAvailable(poolQuestionId));
    }

    @Transactional
    public void markFailed(Long candidateId) {
        repository.findById(candidateId)
                .ifPresent(LevelTestQuestionCandidate::markFailed);
    }

    @Transactional
    public void selectAndExpireOthers(
            Long candidateId,
            Long sessionId,
            int questionNumber
    ) {
        repository.findById(candidateId)
                .ifPresent(value -> value.select(LocalDateTime.now()));
        repository.findAllBySessionIdAndQuestionNumberAndStatus(
                        sessionId,
                        questionNumber,
                        LevelTestQuestionCandidateStatus.AVAILABLE
                )
                .forEach(LevelTestQuestionCandidate::expire);
    }

    @Transactional
    public void expireAvailableForQuestion(
            Long sessionId,
            int questionNumber
    ) {
        repository.findAllBySessionIdAndQuestionNumberAndStatus(
                        sessionId,
                        questionNumber,
                        LevelTestQuestionCandidateStatus.AVAILABLE
                )
                .forEach(LevelTestQuestionCandidate::expire);
    }

    @Transactional
    public void expire(Long candidateId) {
        repository.findById(candidateId)
                .ifPresent(LevelTestQuestionCandidate::expire);
    }

    public record Reservation(Long candidateId, boolean acquired) {
    }
}
