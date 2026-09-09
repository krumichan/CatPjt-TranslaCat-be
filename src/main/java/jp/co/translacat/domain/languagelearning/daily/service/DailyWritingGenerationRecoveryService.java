package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyWritingGenerationRecoveryService {

    private final DailyWritingSetRepository dailySetRepository;
    private final DailyWritingGenerationExecutor generationExecutor;
    private final Set<Long> dispatchedIds = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${language-learning.writing.generation-recovery-delay-ms:5000}")
    public void recover() {
        dailySetRepository.findTop20ByStatusAndGenerationLeaseUntilIsNullOrderByIdAsc(DailySetStatus.GENERATING)
                .forEach(set -> dispatch(set.getId()));
        dailySetRepository.findTop20ByStatusAndGenerationLeaseUntilLessThanEqualOrderByIdAsc(
                DailySetStatus.GENERATING, LocalDateTime.now())
                .forEach(set -> dispatch(set.getId()));
    }

    public void dispatch(Long dailySetId) {
        if (!dispatchedIds.add(dailySetId)) {
            return;
        }
        try {
            generationExecutor.execute(dailySetId).whenComplete((ignored, failure) -> {
                dispatchedIds.remove(dailySetId);
                if (failure != null) {
                    log.warn("Writing generation worker interrupted. dailySetId={}", dailySetId, failure);
                }
            });
        } catch (TaskRejectedException e) {
            dispatchedIds.remove(dailySetId);
            // Work remains persisted as GENERATING and the next scan picks it up.
            log.debug("Writing generation queue full. dailySetId={}", dailySetId);
        } catch (RuntimeException e) {
            dispatchedIds.remove(dailySetId);
            throw e;
        }
    }
}
