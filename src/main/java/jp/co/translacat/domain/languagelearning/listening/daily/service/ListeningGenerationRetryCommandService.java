package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDailySetStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningGenerationCommand;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;
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

    private static final int MANUAL_RETRY_ATTEMPT = 1;

    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningPolicySettingQueryService policySettingService;
    private final ListeningOutboxCommandService outboxCommandService;

    @Transactional
    public ListeningDailySet retry(Long userId, Long dailySetId) {
        ListeningDailySet dailySet = dailySetRepository.findLockedById(dailySetId)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(
                        "Listening Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
                ));

        if (dailySet.getStatus() != ListeningDailySetStatus.FAILED
                || dailySet.getPhysicalItemCount() != 0) {
            throw invalidState("수동 생성 재시도를 실행할 수 없습니다.");
        }
        if (policySettingService.get().getManualRetryLimit()
                < MANUAL_RETRY_ATTEMPT) {
            throw invalidState("Listening 생성 수동 재시도 한도를 초과했습니다.");
        }

        String idempotencyKey = "listening:set:" + dailySetId
                + ":generate:manual:" + MANUAL_RETRY_ATTEMPT;
        ListeningOutboxEvent event = outboxCommandService.enqueue(
                ListeningOutboxType.GENERATE_SET,
                dailySetId,
                ListeningGenerationCommand.manualRetry(MANUAL_RETRY_ATTEMPT),
                idempotencyKey
        );

        if (event.getStatus() == ListeningOutboxStatus.FAILED
                || event.getStatus() == ListeningOutboxStatus.SUCCEEDED) {
            throw invalidState("Listening 생성 수동 재시도 한도를 초과했습니다.");
        }

        dailySet.restartGeneration();
        return dailySet;
    }

    private BusinessException invalidState(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.LISTENING_INVALID_STATE
        );
    }
}
