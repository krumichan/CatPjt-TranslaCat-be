package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingGenerationContext;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyWritingGenerationCommandService {

    private final DailyWritingSetRepository dailyWritingSetRepository;
    private final DailySetClaimCommandService dailySetClaimCommandService;
    private final DailyWritingGenerationContextService generationContextService;
    private final DailyWritingSnapshotService snapshotService;
    private final DailyWritingGenerationStateCommandService generationStateCommandService;
    private final DailyWritingGenerationRecoveryService recoveryService;
    private final DailyWritingCompletionCommandService completionService;

    public DailyWritingSet getOrGenerateToday(Long userId, DailyWritingType writingType) {
        DailyWritingGenerationContext context =
                generationContextService.prepare(userId);

        DailyWritingSet existing = dailyWritingSetRepository
                .findByUserIdAndLearningDateAndWritingType(
                        userId,
                        context.learningDate(),
                        writingType
                )
                .orElse(null);
        if (existing != null) {
            return handleExistingSet(existing);
        }

        DailyWritingSnapshot snapshot = snapshotService.create(
                userId,
                context.learningDate(),
                context.userSetting(),
                context.adminSetting(),
                context.sentenceCount(),
                context.difficultyDistribution()
        );
        DailyWritingSet dailySet = claimDailySet(
                userId,
                context.learningDate(),
                writingType,
                snapshot
        );

        if (dailySet.getStatus() != DailySetStatus.GENERATING) {
            return handleExistingSet(dailySet);
        }

        recoveryService.dispatch(dailySet.getId());
        return dailySet;
    }

    public DailyWritingSet retryGeneration(Long userId, Long dailySetId) {
        DailyWritingSet dailySet = generationStateCommandService.retry(userId, dailySetId);
        if (dailySet.getStatus() == DailySetStatus.GENERATING) {
            recoveryService.dispatch(dailySetId);
        }
        return dailySet;
    }

    private DailyWritingSet claimDailySet(
            Long userId,
            LocalDate learningDate,
            DailyWritingType writingType,
            DailyWritingSnapshot snapshot
    ) {
        try {
            DailySetClaimCommandService.ClaimResult claim =
                    dailySetClaimCommandService.claim(
                            userId,
                            learningDate,
                            writingType,
                            snapshot.snapshotId(),
                            snapshot.sentenceCount(),
                            snapshotService.write(snapshot)
                    );

            return getDailySet(claim.dailySetId());
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            return dailyWritingSetRepository
                    .findByUserIdAndLearningDateAndWritingType(userId, learningDate, writingType)
                    .orElseThrow(() -> new BusinessException(
                            "Daily Set 동시 생성 충돌이 발생했습니다.",
                            LanguageLearningErrorCode.DAILY_SET_GENERATING
                    ));
        }
    }

    private DailyWritingSet handleExistingSet(DailyWritingSet dailySet) {
        // Polling is a read: PARTIAL/FAILED needs an explicit retry command.
        // Reconcile the crash gap between committing the last evaluation and
        // marking completion; the returned entity has been refreshed under lock.
        if (dailySet.getStatus() == DailySetStatus.READY) {
            DailyWritingSet reconciled = completionService.completeIfAllEvaluated(dailySet.getId());
            if (reconciled == null) {
                throw new BusinessException("Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND);
            }
            return reconciled;
        }
        return dailySet;
    }

    private DailyWritingSet getDailySet(Long dailySetId) {
        return dailyWritingSetRepository.findById(dailySetId)
                .orElseThrow(() -> new BusinessException(
                        "Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
                ));
    }
}
