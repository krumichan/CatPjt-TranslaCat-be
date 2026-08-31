package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionCandidate;
import jp.co.translacat.domain.languagelearning.level.pool.repository.LevelTestQuestionCandidateRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LevelTestQuestionCandidateInsertService {

    private final LevelTestQuestionCandidateRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LevelTestQuestionCandidate insertReservation(
            Long sessionId,
            int questionNumber,
            int complexityBand
    ) {
        return repository.saveAndFlush(LevelTestQuestionCandidate.create(
                sessionId,
                questionNumber,
                complexityBand,
                null
        ));
    }
}
