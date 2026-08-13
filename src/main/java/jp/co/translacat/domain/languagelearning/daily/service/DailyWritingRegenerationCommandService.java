package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.factory.DailyWritingGenerationRequestFactory;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.daily.validator.DailyWritingGenerationResponseValidator;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyWritingRegenerationCommandService {

    private static final int MAX_REGENERATIONS = 3;

    private final DailyWritingQueryService dailyWritingQueryService;
    private final DailyWritingItemRepository itemRepository;
    private final WritingAnswerRepository answerRepository;
    private final DailyWritingItemCommandService itemCommandService;
    private final LanguageLearningAiClient aiClient;
    private final DailyWritingSnapshotService snapshotService;
    private final DailyWritingGenerationRequestFactory requestFactory;
    private final DailyWritingGenerationResponseValidator responseValidator;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;

    @Transactional(noRollbackFor = BusinessException.class)
    public DailyWritingSet regenerateUnanswered(
            Long userId,
            Long dailySetId
    ) {
        validateAdaptiveWritingEnabled();

        DailyWritingSet dailySet = dailyWritingQueryService.getOwnedSet(
                userId,
                dailySetId
        );
        validateRegenerationLimit(dailySet);

        List<DailyWritingItem> unansweredItems = findUnansweredItems(
                dailySetId
        );
        if (unansweredItems.isEmpty()) {
            throw new BusinessException(
                    "재생성 가능한 미응답 문제가 없습니다.",
                    LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }

        DailyWritingSnapshot snapshot = snapshotService.read(dailySet);
        DifficultyDistributionDto distribution = distributionFrom(
                unansweredItems
        );
        AiDailyWritingGenerationResponseDto response = aiClient.generateDaily(
                requestFactory.createRegeneration(
                        dailySet,
                        snapshot,
                        unansweredItems.size(),
                        distribution
                )
        );

        responseValidator.validate(
                response,
                unansweredItems.size(),
                distribution
        );
        itemCommandService.replaceAll(
                unansweredItems,
                response.items()
        );
        dailySet.incrementRegeneration();

        return dailySet;
    }

    private List<DailyWritingItem> findUnansweredItems(Long dailySetId) {
        return itemRepository.findAllByDailySetIdOrderByOrderNoAsc(dailySetId)
                .stream()
                .filter(item -> !answerRepository.existsByDailyItemId(
                        item.getId()
                ))
                .toList();
    }

    private DifficultyDistributionDto distributionFrom(
            List<DailyWritingItem> items
    ) {
        int reviewCount = 0;
        int normalCount = 0;
        int challengeCount = 0;

        for (DailyWritingItem item : items) {
            switch (item.getDifficulty()) {
                case REVIEW -> reviewCount++;
                case NORMAL -> normalCount++;
                case CHALLENGE -> challengeCount++;
            }
        }

        return new DifficultyDistributionDto(
                reviewCount,
                normalCount,
                challengeCount
        );
    }

    private void validateRegenerationLimit(DailyWritingSet dailySet) {
        if (dailySet.getRegenerationCount() >= MAX_REGENERATIONS) {
            throw new BusinessException(
                    "문제 재생성 가능 횟수를 초과했습니다.",
                    LanguageLearningErrorCode.REGENERATION_LIMIT
            );
        }
    }

    private void validateAdaptiveWritingEnabled() {
        if (!adminSettingQueryService
                .getOrCreateEntity()
                .isAdaptiveWritingEnabled()) {
            throw new BusinessException(
                    "Adaptive Writing이 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
    }
}
