package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDailySetStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningGenerationCommand;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListeningGenerationRetryCommandService {

    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningItemRepository itemRepository;
    private final ListeningPolicySettingQueryService policySettingService;
    private final ListeningOutboxCommandService outboxCommandService;
    private final ListeningOutboxEventRepository outboxRepository;

    @Transactional
    public ListeningDailySet retry(Long userId, Long dailySetId) {
        ListeningDailySet dailySet = dailySetRepository.findLockedById(dailySetId)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(
                        "Listening Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
                ));

        if (outboxRepository.existsByEventTypeAndAggregateIdAndStatusIn(
                ListeningOutboxType.GENERATE_SET, dailySetId,
                java.util.List.of(ListeningOutboxStatus.PENDING, ListeningOutboxStatus.PROCESSING))) {
            return dailySet;
        }

        if ((dailySet.getStatus() != ListeningDailySetStatus.FAILED
                && dailySet.getStatus() != ListeningDailySetStatus.PARTIAL)
                || dailySet.getFailureReason() == null) {
            throw invalidState("수동 생성 재시도를 실행할 수 없습니다.");
        }
        int missingIndex = 1;
        while (missingIndex <= dailySet.getTargetItemCount()
                && itemRepository.existsByDailySetIdAndItemIndex(dailySetId, missingIndex)) {
            missingIndex++;
        }
        if (missingIndex > dailySet.getTargetItemCount()) {
            throw invalidState("생성을 재시도할 누락 문항이 없습니다.");
        }
        int retryLimit = policySettingService.get().getManualRetryLimit();
        for (int attempt = 1; attempt <= retryLimit; attempt++) {
            String idempotencyKey = "listening:set:" + dailySetId
                    + ":generate:item:" + missingIndex + ":manual:" + attempt;
            ListeningOutboxEvent event = outboxCommandService.enqueue(
                    ListeningOutboxType.GENERATE_SET, dailySetId,
                    ListeningGenerationCommand.item(missingIndex, attempt), idempotencyKey);
            if (event.getStatus() == ListeningOutboxStatus.PENDING
                    || event.getStatus() == ListeningOutboxStatus.PROCESSING) {
                dailySet.restartGeneration();
                return dailySet;
            }
        }
        throw invalidState("Listening 생성 수동 재시도 한도를 초과했습니다.");
    }

    private BusinessException invalidState(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.LISTENING_INVALID_STATE
        );
    }
}
