package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LevelTestEvaluationStateService {

    private final LevelTestItemRepository itemRepository;
    private final LevelTestSessionRepository sessionRepository;

    @Transactional
    public void markFailed(Long itemId) {
        LevelTestItem item = itemRepository
                .findLockedById(itemId)
                .orElseThrow();
        item.markEvaluationFailed();
        sessionRepository.findLockedById(item.getSession().getId())
                .ifPresent(session -> session.resumeAfterEvaluation(
                        LocalDateTime.now()
                ));
    }
}
