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
    private final DailyWritingGenerationExecutor generationExecutor;

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

        return generationExecutor.execute(dailySet, snapshot);
    }

    public DailyWritingSet retryFailedGeneration(Long dailySetId) {
        DailyWritingSet dailySet = getDailySet(dailySetId);

        if (dailySet.getStatus() != DailySetStatus.FAILED) {
            return dailySet;
        }

        return generationExecutor.execute(
                dailySet,
                snapshotService.read(dailySet)
        );
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
        if (dailySet.getStatus() == DailySetStatus.GENERATING) {
            throw new BusinessException(
                    "Daily Set 생성이 진행 중입니다.",
                    LanguageLearningErrorCode.DAILY_SET_GENERATING
            );
        }

        if (dailySet.getStatus() == DailySetStatus.FAILED) {
            return generationExecutor.execute(
                    dailySet,
                    snapshotService.read(dailySet)
            );
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
