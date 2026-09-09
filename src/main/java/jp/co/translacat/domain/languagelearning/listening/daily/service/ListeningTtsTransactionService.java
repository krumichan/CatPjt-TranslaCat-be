package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningGenerationCommand;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;

import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ListeningTtsTransactionService {

    private static final String DEFAULT_LISTENING_VOICE_ID = "Kore";

    private final ListeningItemRepository itemRepository;
    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningPolicySettingQueryService policySettingService;
    private final ListeningAudioKeyFactory audioKeyFactory;
    private final ListeningOutboxCommandService outboxCommandService;
    private final ListeningOutboxTransactionService outboxTransactionService;
    private final LanguageLearningJsonCodec jsonCodec;
    private final EntityManager entityManager;
    private final ListeningOutboxEventRepository outboxRepository;

    @Transactional(readOnly = true)
    public TtsWork prepare(
            ListeningOutboxTransactionService.ClaimedEvent event
    ) {
        ListeningItem item = itemRepository.findById(event.aggregateId())
                .orElseThrow();
        ListeningPolicySetting policy = policySettingService.get();
        String requestId = "be-listening-tts-" + event.id();
        AiListeningContract.Voice voice = new AiListeningContract.Voice(
                item.getDailySet().getLearningLanguage(),
                DEFAULT_LISTENING_VOICE_ID,
                "current",
                "STANDARD"
        );
        AiListeningContract.TtsRequest request =
                new AiListeningContract.TtsRequest(
                        requestId,
                        event.idempotencyKey(),
                        item.getId(),
                        item.getSourceText(),
                        item.getContentHash(),
                        item.getDailySet().getGenerationVersion(),
                        item.getDailySet().getLearningLanguage(),
                        voice,
                        "NORMAL",
                        policy.getProfilePolicyVersion(),
                        policy.getModelConfigVersion(),
                        policy.getAutomaticRetryLimit(),
                        item.getManualTtsRetryCount()
                );
        String objectKey = audioKeyFactory.reference(
                item.getDailySet().getUser().getId(),
                item.getDailySet().getId(),
                item.getId(),
                "wav"
        );

        return new TtsWork(
                event,
                request,
                objectKey,
                policy.getReferenceAudioMaxSeconds(),
                policy.getMaxAudioFileBytes(),
                policy.getReferenceAudioRetentionDays()
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void apply(
            TtsWork work,
            AiListeningContract.TtsResponse response,
            String contentType
    ) {
        ListeningItem item = lockedItem(work.event().aggregateId());
        if (!outboxTransactionService.ownsClaim(work.event())) {
            return;
        }

        if (item.getStatus() != ListeningItemStatus.TTS_PENDING
                || item.getManualTtsRetryCount() != work.request().manualRetryAttempt()) {
            outboxTransactionService.succeed(
                    work.event().id(),
                    LocalDateTime.now()
            );
            return;
        }

        item.markTtsReady(
                work.objectKey(),
                response.audio().durationMs(),
                contentType,
                response.audio().checksum(),
                jsonCodec.write(response.audio().voice()),
                LocalDateTime.now().plusDays(work.retentionDays())
        );
        refreshSetState(item.getDailySet());
        outboxTransactionService.succeed(
                work.event().id(),
                LocalDateTime.now()
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recordFailure(
            ListeningOutboxTransactionService.ClaimedEvent event,
            String reason,
            boolean retryable,
            Duration retryAfter
    ) {
        ListeningItem item = lockedItem(event.aggregateId());
        if (!outboxTransactionService.ownsClaim(event)) {
            return;
        }
        if (item.getStatus() != ListeningItemStatus.TTS_PENDING) {
            outboxTransactionService.succeed(event.id(), LocalDateTime.now());
            return;
        }
        var result = outboxTransactionService.fail(event, reason, retryable,
                retryAfter, policySettingService.get().getAutomaticRetryLimit(),
                LocalDateTime.now());

        if (retryable && !result.exhausted()) {
            item.registerAutomaticTtsRetry(reason);
        }

        if (!result.exhausted()) {
            return;
        }

        markNotEvaluableAndScheduleReplacement(item, reason);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void abandonOrphan(Long itemId, String reason) {
        ListeningItem item = lockedItem(itemId);

        if (item.getStatus() != ListeningItemStatus.TTS_PENDING) {
            return;
        }

        markNotEvaluableAndScheduleReplacement(item, reason);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recoverOrphan(Long itemId) {
        ListeningItem item = lockedItem(itemId);
        if (item.getStatus() != ListeningItemStatus.TTS_PENDING) {
            return;
        }
        var latest = outboxRepository.findFirstByEventTypeAndAggregateIdOrderByIdDesc(
                ListeningOutboxType.GENERATE_TTS, itemId);
        if (latest.isPresent() && (latest.get().getStatus() == ListeningOutboxStatus.PENDING
                || latest.get().getStatus() == ListeningOutboxStatus.PROCESSING)) {
            return;
        }
        if (latest.isPresent() && latest.get().getIdempotencyKey().contains(":tts:recovery:")) {
            markNotEvaluableAndScheduleReplacement(item,
                    "TTS 복구 작업이 완료되지 않아 대체 문항 생성을 시도합니다.");
            return;
        }
        String suffix = latest.map(value -> "after-" + value.getId()).orElse("missing");
        outboxCommandService.enqueue(ListeningOutboxType.GENERATE_TTS, itemId, null,
                "listening:item:" + itemId + ":tts:recovery:" + suffix);
    }

    private void markNotEvaluableAndScheduleReplacement(
            ListeningItem item,
            String reason
    ) {
        item.markNotEvaluable(reason);
        ListeningDailySet set = item.getDailySet();
        ListeningPolicySetting policy = policySettingService.get();

        int missingSlots = 0;
        for (int index = 1; index <= set.getTargetItemCount(); index++) {
            if (!itemRepository.existsByDailySetIdAndItemIndex(set.getId(), index)) {
                missingSlots++;
            }
        }
        // Reserve capacity for original slots while independent replacements overlap.
        if (set.getPhysicalItemCount() + missingSlots < policy.getHardItemLimit()) {
            int next = item.getReplacementSequence() + 1;
            outboxCommandService.enqueue(
                    ListeningOutboxType.GENERATE_SET,
                    set.getId(),
                    new ListeningGenerationCommand(
                            item.getId(),
                            item.getItemIndex(),
                            next,
                            0
                    ),
                    "listening:set:" + set.getId()
                            + ":replace:" + item.getItemIndex() + ":" + next
                            + ":tts-manual:" + item.getManualTtsRetryCount()
            );
        } else if (set.getFailureReason() == null) {
            set.fail(reason == null || reason.isBlank()
                    ? "Listening 음성 생성에 실패했습니다." : reason);
        }

        refreshSetState(set);
    }

    private void refreshSetState(ListeningDailySet set) {
        long ready = itemRepository.countLogicalItemsByStatus(
                set.getId(),
                ListeningItemStatus.READY
        );
        long pending = itemRepository.countLogicalItemsByStatus(
                set.getId(), ListeningItemStatus.TTS_PENDING);
        set.refreshAvailability(ready, pending);
    }

    private ListeningItem lockedItem(Long itemId) {
        ListeningItem snapshot = itemRepository.findById(itemId).orElseThrow();
        dailySetRepository.findLockedById(snapshot.getDailySet().getId()).orElseThrow();
        ListeningItem item = itemRepository.findLockedById(itemId).orElseThrow();
        // The parent lock may have waited while another callback changed this item.
        entityManager.refresh(item, LockModeType.PESSIMISTIC_WRITE);
        return item;
    }

    public record TtsWork(
            ListeningOutboxTransactionService.ClaimedEvent event,
            AiListeningContract.TtsRequest request,
            String objectKey,
            int maxAudioSeconds,
            long maxAudioBytes,
            int retentionDays
    ) {
    }
}
