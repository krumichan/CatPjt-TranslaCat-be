package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyWritingGenerationStateCommandService {

    private final DailyWritingSetRepository dailyWritingSetRepository;
    private final DailyWritingItemCommandService itemCommandService;
    private final DailyWritingItemRepository itemRepository;

    @Transactional
    public GenerationClaim claim(Long dailySetId) {
        DailyWritingSet dailySet = getDailySet(dailySetId);
        LocalDateTime now = LocalDateTime.now();
        if (!dailySet.canClaimGeneration(now)) {
            return null;
        }
        int order = firstMissingOrder(dailySet);
        if (order > dailySet.getSentenceCount()) {
            dailySet.ready(dailySet.getPromptVersion());
            return null;
        }
        String token = UUID.randomUUID().toString();
        dailySet.claimGeneration(token, now.plusMinutes(20));
        return new GenerationClaim(dailySet, order, token);
    }

    @Transactional
    public boolean publish(
            Long dailySetId,
            String token,
            int order,
            String learningLanguage,
            DailyWritingGeneratedItemDto generatedItem,
            String promptVersion
    ) {
        DailyWritingSet dailySet = getDailySet(dailySetId);
        if (!dailySet.ownsGeneration(token) || firstMissingOrder(dailySet) != order) {
            return false;
        }
        DailyWritingGeneratedItemDto indexed = new DailyWritingGeneratedItemDto(
                order, generatedItem.difficulty(), generatedItem.originText(),
                generatedItem.keywords(), generatedItem.focusMetrics(), generatedItem.focusReason(),
                generatedItem.providedFacts(), generatedItem.requiredIntents(),
                generatedItem.responseConstraints(), generatedItem.languageComplexityBand(),
                generatedItem.diversityMetadata()
        );
        itemCommandService.createAll(dailySet, learningLanguage, java.util.List.of(indexed));
        dailySet.itemGenerated(promptVersion);
        if (firstMissingOrder(dailySet) > dailySet.getSentenceCount()) {
            dailySet.ready(promptVersion);
        }
        return true;
    }

    @Transactional
    public void fail(Long dailySetId, String token, String message) {
        DailyWritingSet dailySet = getDailySet(dailySetId);
        if (dailySet.ownsGeneration(token)) {
            dailySet.failGeneration(message,
                    !itemRepository.findAllByDailySetIdOrderByOrderNoAsc(dailySetId).isEmpty());
        }
    }

    @Transactional
    public DailyWritingSet retry(Long userId, Long dailySetId) {
        DailyWritingSet dailySet = getDailySet(dailySetId);
        if (!dailySet.getUser().getId().equals(userId)) {
            throw new BusinessException("Daily Set을 찾을 수 없습니다.",
                    LanguageLearningErrorCode.DAILY_SET_NOT_FOUND);
        }
        // Concurrent retries are idempotent and never replace already published items.
        if (dailySet.getStatus() == DailySetStatus.PARTIAL
                || dailySet.getStatus() == DailySetStatus.FAILED) {
            if (firstMissingOrder(dailySet) > dailySet.getSentenceCount()) {
                dailySet.ready(dailySet.getPromptVersion());
            } else {
                dailySet.restartGeneration(dailySet.getSnapshotJson());
            }
        }
        return dailySet;
    }

    private int firstMissingOrder(DailyWritingSet dailySet) {
        Set<Integer> orders = itemRepository.findAllByDailySetIdOrderByOrderNoAsc(dailySet.getId())
                .stream().map(DailyWritingItem::getOrderNo).collect(Collectors.toSet());
        for (int order = 1; order <= dailySet.getSentenceCount(); order++) {
            if (!orders.contains(order)) {
                return order;
            }
        }
        return dailySet.getSentenceCount() + 1;
    }

    private DailyWritingSet getDailySet(Long dailySetId) {
        return dailyWritingSetRepository.findLockedById(dailySetId)
                .orElseThrow(() -> new BusinessException(
                        "Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
                ));
    }

    public record GenerationClaim(DailyWritingSet dailySet, int order, String token) {
    }
}
