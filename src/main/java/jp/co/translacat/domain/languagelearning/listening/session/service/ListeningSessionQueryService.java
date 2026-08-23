package jp.co.translacat.domain.languagelearning.listening.session.service;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.service.ListeningViewMapper;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListeningSessionQueryService {

    private final ListeningSessionRepository sessionRepository;
    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningViewMapper viewMapper;

    public ListeningApiContract.SessionView view(Long userId, Long sessionId) {
        return viewMapper.session(owned(userId, sessionId));
    }

    public ListeningApiContract.SessionResultView result(
            Long userId,
            Long sessionId
    ) {
        ListeningSession session = owned(userId, sessionId);
        var attempts = attemptRepository
                .findAllBySessionIdOrderByItemItemIndexAscAttemptNoAsc(
                        session.getId()
                );
        var official = attempts.stream()
                .filter(ListeningItemAttempt::isOfficial)
                .toList();
        Double average = official.stream()
                .filter(value -> value.getOverallScore() != null)
                .mapToDouble(ListeningItemAttempt::getOverallScore)
                .average()
                .stream().boxed().findFirst().orElse(null);
        double coverage = official.isEmpty()
                ? 0
                : official.stream()
                        .mapToDouble(ListeningItemAttempt::getCoverage)
                        .average()
                        .orElse(0);

        return new ListeningApiContract.SessionResultView(
                session.getId(),
                session.getStatus(),
                session.getCompletedItemCount(),
                session.getEvaluatedItemCount(),
                average,
                coverage,
                attempts.stream().map(viewMapper::attempt).toList()
        );
    }

    public ListeningSession owned(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Listening Session을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.SESSION_NOT_FOUND
                ));
    }
}
