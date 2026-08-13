package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingGenerationContext;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyWritingGenerationCommandService {

    private final DailyWritingSetRepository dailyWritingSetRepository;
    private final DailySetClaimCommandService dailySetClaimCommandService;
    private final DailyWritingGenerationContextService generationContextService;
    private final DailyWritingSnapshotService snapshotService;
    private final DailyWritingGenerationExecutor generationExecutor;

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = BusinessException.class
    )
    public DailyWritingSet getOrGenerateToday(Long userId) {
        DailyWritingGenerationContext context =
                generationContextService.prepare(userId);

        DailyWritingSet existing = dailyWritingSetRepository
                .findByUserIdAndLearningDate(
                        userId,
                        context.learningDate()
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
                snapshot
        );

        if (dailySet.getStatus() != DailySetStatus.GENERATING) {
            return handleExistingSet(dailySet);
        }

        return generationExecutor.execute(dailySet, snapshot);
    }

    @Transactional(noRollbackFor = BusinessException.class)
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
            DailyWritingSnapshot snapshot
    ) {
        try {
            DailySetClaimCommandService.ClaimResult claim =
                    dailySetClaimCommandService.claim(
                            userId,
                            learningDate,
                            snapshot.snapshotId(),
                            snapshot.sentenceCount(),
                            snapshotService.write(snapshot)
                    );

            return getDailySet(claim.dailySetId());
        } catch (DataIntegrityViolationException e) {
            return dailyWritingSetRepository
                    .findByUserIdAndLearningDate(userId, learningDate)
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
