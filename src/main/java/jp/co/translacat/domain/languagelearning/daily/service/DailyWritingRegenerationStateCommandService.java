package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.factory.DailyWritingGenerationRequestFactory;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DailyWritingRegenerationStateCommandService {

    private static final int MAX_REGENERATIONS = 3;
    private static final int REGENERATION_LEASE_MINUTES = 10;

    private final DailyWritingSetRepository dailySetRepository;
    private final DailyWritingItemRepository itemRepository;
    private final WritingAnswerRepository answerRepository;
    private final DailyWritingSnapshotService snapshotService;
    private final DailyWritingGenerationRequestFactory requestFactory;
    private final DailyWritingItemCommandService itemCommandService;
    private final DailyWritingItemRevisionService itemRevisionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegenerationClaim claim(
            Long userId,
            Long dailySetId
    ) {
        DailyWritingSet dailySet = getLockedSet(dailySetId);
        validateOwnership(dailySet, userId);
        validateState(dailySet);
        validateRegenerationLimit(dailySet);

        LocalDateTime now = LocalDateTime.now();
        if (!dailySet.canClaimRegeneration(now)) {
            throw new BusinessException(
                    "문제 재생성이 이미 진행 중입니다.",
                    LanguageLearningErrorCode.WRITING_REGENERATION_IN_PROGRESS
            );
        }

        List<DailyWritingItem> unansweredItems = findUnansweredItems(
                dailySetId
        );
        if (unansweredItems.isEmpty()) {
            throw new BusinessException(
                    "재생성 가능한 미응답 문제가 없습니다.",
                    LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }

        String token = UUID.randomUUID().toString();
        dailySet.claimRegeneration(
                token,
                now.plusMinutes(REGENERATION_LEASE_MINUTES)
        );

        DailyWritingSnapshot snapshot = snapshotService.read(dailySet);
        DifficultyDistributionDto distribution = distributionFrom(
                unansweredItems
        );
        AiDailyWritingGenerationRequestDto request = requestFactory
                .createRegeneration(
                        userId,
                        dailySet,
                        snapshot,
                        unansweredItems.size(),
                        distribution,
                        token
                );

        List<RegenerationTarget> targets = unansweredItems.stream()
                .sorted(Comparator.comparingInt(
                        DailyWritingItem::getOrderNo
                ))
                .map(item -> new RegenerationTarget(
                        item.getId(),
                        item.getOrderNo(),
                        item.getDifficulty(),
                        itemRevisionService.revision(item)
                ))
                .toList();

        return new RegenerationClaim(
                dailySet.getId(),
                token,
                request,
                targets,
                distribution,
                dailySet.getWritingType(),
                snapshot.learningLanguage()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyWritingSet publish(
            RegenerationClaim claim,
            AiDailyWritingGenerationResponseDto response
    ) {
        DailyWritingSet dailySet = getLockedSet(claim.dailySetId());
        if (dailySet.getStatus() == DailySetStatus.GENERATING
                || dailySet.getStatus() == DailySetStatus.PARTIAL
                || dailySet.getStatus() == DailySetStatus.FAILED) {
            throw regenerationConflict(
                    "재생성 중 Daily Set 상태가 변경되었습니다."
            );
        }
        if (!dailySet.ownsRegeneration(claim.token())) {
            throw regenerationConflict(
                    "문제 재생성 소유권이 변경되었습니다."
            );
        }
        if (response == null
                || !claim.request().requestId().equals(
                response.requestId()
        )) {
            throw new BusinessException(
                    "AI 재생성 응답 식별자가 일치하지 않습니다.",
                    LanguageLearningErrorCode.AI_SCHEMA_INVALID
            );
        }

        List<DailyWritingItem> currentItems = itemRepository
                .findAllByDailySetIdOrderByOrderNoAsc(
                        claim.dailySetId()
                );
        Map<Long, DailyWritingItem> currentById = new HashMap<>();
        for (DailyWritingItem item : currentItems) {
            currentById.put(item.getId(), item);
        }

        List<DailyWritingItem> replaceTargets = new ArrayList<>();
        for (RegenerationTarget target : claim.targets()) {
            DailyWritingItem current = currentById.get(target.itemId());
            if (current == null
                    || current.getOrderNo() != target.orderNo()
                    || current.getDifficulty() != target.difficulty()
                    || !target.contentRevision().equals(
                    itemRevisionService.revision(current)
            )) {
                throw regenerationConflict(
                        "재생성 대상 문제가 변경되었습니다."
                );
            }
            if (answerRepository.existsByDailyItemId(
                    target.itemId()
            )) {
                throw regenerationConflict(
                        "재생성 대상 문제에 답변이 제출되었습니다."
                );
            }
            replaceTargets.add(current);
        }

        List<DailyWritingGeneratedItemDto> generatedItems =
                response.items();
        itemCommandService.replaceAll(
                claim.learningLanguage(),
                replaceTargets,
                generatedItems
        );
        dailySet.incrementRegeneration();
        dailySet.releaseRegeneration(claim.token());
        return dailySet;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(
            Long dailySetId,
            String token
    ) {
        dailySetRepository.findLockedById(dailySetId)
                .ifPresent(dailySet ->
                        dailySet.releaseRegeneration(token)
                );
    }

    private List<DailyWritingItem> findUnansweredItems(
            Long dailySetId
    ) {
        return itemRepository.findAllByDailySetIdOrderByOrderNoAsc(
                        dailySetId
                )
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

    private void validateOwnership(
            DailyWritingSet dailySet,
            Long userId
    ) {
        if (!dailySet.getUser().getId().equals(userId)) {
            throw new BusinessException(
                    "Daily Set을 찾을 수 없습니다.",
                    LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
            );
        }
    }

    private void validateState(DailyWritingSet dailySet) {
        if (dailySet.getStatus() == DailySetStatus.GENERATING
                || dailySet.getStatus() == DailySetStatus.PARTIAL
                || dailySet.getStatus() == DailySetStatus.FAILED) {
            throw new BusinessException(
                    "생성 중이거나 일부만 생성된 문제는 생성 재시도를 이용해주세요.",
                    LanguageLearningErrorCode.DAILY_SET_GENERATING
            );
        }
    }

    private void validateRegenerationLimit(
            DailyWritingSet dailySet
    ) {
        if (dailySet.getRegenerationCount() >= MAX_REGENERATIONS) {
            throw new BusinessException(
                    "문제 재생성 가능 횟수를 초과했습니다.",
                    LanguageLearningErrorCode.REGENERATION_LIMIT
            );
        }
    }

    private DailyWritingSet getLockedSet(Long dailySetId) {
        return dailySetRepository.findLockedById(dailySetId)
                .orElseThrow(() -> new BusinessException(
                        "Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
                ));
    }

    private BusinessException regenerationConflict(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.WRITING_REGENERATION_CONFLICT
        );
    }

    public record RegenerationClaim(
            Long dailySetId,
            String token,
            AiDailyWritingGenerationRequestDto request,
            List<RegenerationTarget> targets,
            DifficultyDistributionDto distribution,
            DailyWritingType writingType,
            String learningLanguage
    ) {
        public int expectedCount() {
            return targets.size();
        }
    }

    public record RegenerationTarget(
            Long itemId,
            int orderNo,
            DailyWritingDifficulty difficulty,
            String contentRevision
    ) {
    }
}
