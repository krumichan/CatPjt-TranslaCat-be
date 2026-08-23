package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListeningTtsRetryCommandService {

    private final ListeningItemRepository itemRepository;
    private final ListeningPolicySettingQueryService policySettingService;
    private final ListeningOutboxCommandService outboxCommandService;

    @Transactional
    public ListeningDailySet retry(
            Long userId,
            Long itemId
    ) {
        ListeningItem item = itemRepository.findLockedById(itemId)
                .filter(value -> value.getDailySet().getUser().getId()
                        .equals(userId))
                .orElseThrow(() -> new BusinessException(
                        "Listening 문항을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_ITEM_NOT_FOUND
                ));
        int limit = policySettingService.get().getManualRetryLimit();

        if (item.getStatus() != ListeningItemStatus.NOT_EVALUABLE
                || item.getManualTtsRetryCount() >= limit) {
            throw new BusinessException(
                    "수동 TTS 재시도를 실행할 수 없습니다.",
                    LanguageLearningErrorCode.LISTENING_INVALID_STATE
            );
        }

        item.startManualTtsRetry(limit);
        outboxCommandService.enqueue(
                ListeningOutboxType.GENERATE_TTS,
                item.getId(),
                null,
                "listening:item:" + item.getId()
                        + ":tts:manual:" + item.getManualTtsRetryCount()
        );

        return item.getDailySet();
    }
}
