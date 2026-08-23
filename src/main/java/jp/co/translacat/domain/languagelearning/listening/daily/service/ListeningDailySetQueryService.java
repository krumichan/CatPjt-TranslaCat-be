package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListeningDailySetQueryService {

    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningItemRepository itemRepository;
    private final ListeningPolicySettingQueryService policySettingService;

    public ListeningDailySet owned(Long userId, Long dailySetId) {
        return dailySetRepository.findById(dailySetId)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(
                        "Listening Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
                ));
    }

    public ListeningApiContract.DailySetView view(
            Long userId,
            ListeningDailySet dailySet
    ) {
        if (!dailySet.getUser().getId().equals(userId)) {
            throw new BusinessException(
                    "Listening Daily Set을 찾을 수 없습니다.",
                    LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
            );
        }

        List<ListeningItem> active = activeItems(dailySet.getId());
        LocalDateTime now = LocalDateTime.now();

        return new ListeningApiContract.DailySetView(
                dailySet.getId(),
                dailySet.getLearningDate(),
                dailySet.getOriginLanguage(),
                dailySet.getLearningLanguage(),
                dailySet.getDifficulty(),
                dailySet.getStatus(),
                dailySet.getTargetItemCount(),
                dailySet.getPhysicalItemCount(),
                (int) active.stream()
                        .filter(value -> value.getStatus() == ListeningItemStatus.READY)
                        .count(),
                dailySet.getCompletedItemCount(),
                dailySet.getFailureReason(),
                active.stream().map(item -> new ListeningApiContract.ItemSummary(
                        item.getId(),
                        item.getItemIndex(),
                        item.getReplacementSequence(),
                        item.getStatus(),
                        item.isPlayable(now),
                        item.getAudioDurationMs()
                )).toList()
        );
    }

    public List<ListeningItem> activeItems(Long dailySetId) {
        List<ListeningItem> all = itemRepository
                .findAllByDailySetIdOrderByItemIndexAscReplacementSequenceAsc(
                        dailySetId
                );
        Map<Integer, ListeningItem> selected = new LinkedHashMap<>();
        all.stream()
                .sorted(Comparator.comparingInt(ListeningItem::getReplacementSequence))
                .forEach(item -> {
                    ListeningItem previous = selected.get(item.getItemIndex());

                    if (previous == null
                            || item.getStatus() == ListeningItemStatus.READY
                            || previous.getStatus() != ListeningItemStatus.READY) {
                        selected.put(item.getItemIndex(), item);
                    }
                });

        return selected.values().stream()
                .sorted(Comparator.comparingInt(ListeningItem::getItemIndex))
                .toList();
    }

    public ListeningApiContract.PolicyView policy() {
        var value = policySettingService.get();

        return new ListeningApiContract.PolicyView(
                value.isEnabled(),
                value.getDefaultItemCount(),
                value.getMinItemCount(),
                value.getMaxItemCount(),
                value.getHardItemLimit(),
                value.getResumeHours(),
                value.getReferenceAudioRetentionDays(),
                value.getUserAudioRetentionDays(),
                value.getReportedAudioRetentionDays(),
                value.getAutomaticRetryLimit(),
                value.getManualRetryLimit(),
                value.getPracticeAttemptLimit(),
                value.getProfilePolicyVersion(),
                value.getModelConfigVersion(),
                value.isReferenceTtsRegenerationEnabled()
        );
    }
}
