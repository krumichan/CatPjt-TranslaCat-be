package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAttemptStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningLearningMode;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final ListeningSessionRepository sessionRepository;
    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningOutboxEventRepository outboxRepository;

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
                dailySet.getLearningMode(),
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
                )).toList(),
                generationInProgress(dailySet.getId())
        );
    }

    public List<ListeningApiContract.DailyModeStatusView> todayStatuses(
            Long userId,
            LocalDate learningDate,
            String learningLanguage
    ) {
        Map<ListeningLearningMode, ListeningDailySet> byMode = dailySetRepository
                .findAllByUserIdAndLearningDateAndLearningLanguageOrderByIdAsc(
                        userId, learningDate, learningLanguage
                ).stream()
                .collect(java.util.stream.Collectors.toMap(
                        ListeningDailySet::getLearningMode,
                        value -> value,
                        (left, right) -> right
                ));
        return java.util.Arrays.stream(ListeningLearningMode.values())
                .map(mode -> {
                    ListeningDailySet set = byMode.get(mode);
                    if (set == null) {
                        return new ListeningApiContract.DailyModeStatusView(
                                mode, null, null, null, null,
                                0, 0, 0, 0, 0, 0, 0, 0, false, null, false
                        );
                    }
                    ListeningSession latestSession = sessionRepository
                            .findFirstByDailySetIdOrderByStartedAtDesc(set.getId())
                            .orElse(null);
                    var latestAttempts = latestSession == null
                            ? java.util.List.<ListeningItemAttempt>of()
                            : attemptRepository
                                    .findAllBySessionIdOrderByItemItemIndexAscAttemptNoAsc(
                                            latestSession.getId()
                                    );
                    int submittedItemCount = (int) latestAttempts.stream()
                            .filter(value -> value.isOfficial()
                                    && value.getStatus() != ListeningAttemptStatus.READY
                                    && value.getStatus() != ListeningAttemptStatus.IN_PROGRESS)
                            .count();
                    int terminalItemCount = (int) latestAttempts.stream()
                            .filter(value -> value.isOfficial() && value.isFinalized())
                            .count();
                    int answerRevealedItemCount = (int) latestAttempts.stream()
                            .filter(value -> value.isOfficial() && value.isAnswerRevealed())
                            .count();
                    int readyItemCount = (int) activeItems(set.getId()).stream()
                            .filter(value -> value.getStatus() == ListeningItemStatus.READY)
                            .count();
                    boolean completed = set.getStatus()
                            == jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDailySetStatus.COMPLETED
                            || (latestSession != null
                            && latestSession.getStatus()
                            == jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus.COMPLETED);
                    return new ListeningApiContract.DailyModeStatusView(
                            mode,
                            set.getId(),
                            latestSession == null ? null : latestSession.getId(),
                            set.getStatus(),
                            latestSession == null ? null : latestSession.getStatus(),
                            set.getCompletedItemCount(),
                            latestSession == null ? 0 : latestSession.getEvaluatedItemCount(),
                            submittedItemCount,
                            terminalItemCount,
                            answerRevealedItemCount,
                            set.getPhysicalItemCount(),
                            readyItemCount,
                            set.getTargetItemCount(),
                            completed,
                            set.getFailureReason(),
                            generationInProgress(set.getId())
                    );
                })
                .toList();
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

    public boolean generationInProgress(Long dailySetId) {
        return outboxRepository.existsByEventTypeAndAggregateIdAndStatusIn(
                ListeningOutboxType.GENERATE_SET, dailySetId,
                List.of(ListeningOutboxStatus.PENDING, ListeningOutboxStatus.PROCESSING));
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
