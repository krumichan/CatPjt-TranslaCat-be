package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningDurationPolicy;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningGenerationCommand;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.setting.model.ListeningPolicySnapshot;
import jp.co.translacat.domain.languagelearning.listening.setting.port.ListeningPolicyGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListeningTtsTransactionService {

    private static final Set<String> SOURCE_INDEPENDENT_FAILURES = Set.of(
            "PROVIDER_RATE_LIMITED", "PROVIDER_UNAVAILABLE", "PROVIDER_TIMEOUT"
    );

    private final ListeningItemRepository itemRepository;
    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningPolicyGateway policySettingService;
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
        ListeningPolicySnapshot policy = policySettingService.get();
        var metadata = metadata(item);
        var demand = metadata != null && metadata.durationDemand() != null
                ? metadata.durationDemand()
                : ListeningDurationPolicy.effective(item.getDailySet().getDifficulty(),
                1.0, (double) policy.getReferenceAudioMaxSeconds());
        String requestId = "be-listening-tts-" + event.id();
        // The source item owns its synthesis identity. Older pending items lack
        // this snapshot and require an explicit migration; never silently turn
        // their Gemini work into OpenAI audio during a lease retry.
        if (item.getVoiceSnapshotJson() == null || item.getVoiceSnapshotJson().isBlank()) {
            throw new IllegalStateException("LEGACY_TTS_MIGRATION_REQUIRED");
        }
        AiListeningContract.Voice voice = jsonCodec.read(
                item.getVoiceSnapshotJson(), AiListeningContract.Voice.class);
        if (!"openai-speech-v1".equals(voice.version())
                || !java.util.Set.of("marin", "cedar").contains(voice.voiceKey())) {
            throw new IllegalStateException("LEGACY_TTS_MIGRATION_REQUIRED");
        }
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
                        item.getManualTtsRetryCount(),
                        demand
                );
        String objectKey = audioKeyFactory.reference(
                item.getDailySet().getUser().getId(),
                item.getDailySet().getId(),
                item.getId(),
                "wav"
        );
        // Every lease writes an immutable object: a late worker cannot overwrite
        // bytes already published by another claim before the DB fence is checked.
        objectKey = objectKey.substring(0, objectKey.length() - 4)
                + "-claim-" + event.id() + "-" + event.attemptCount() + ".wav";

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
                || item.getManualTtsRetryCount() != work.request().manualRetryAttempt()
                || (work.request().contentHash() != null
                && !item.getContentHash().equals(work.request().contentHash()))
                || item.getAudioObjectKey() != null) {
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
    public void recordDurationFailure(TtsWork work, double measuredSeconds, String code) {
        ListeningItem item = lockedItem(work.event().aggregateId());
        if (!outboxTransactionService.ownsClaim(work.event())) {
            return;
        }
        if (item.getStatus() != ListeningItemStatus.TTS_PENDING
                || item.getManualTtsRetryCount() != work.request().manualRetryAttempt()
                || !item.getContentHash().equals(work.request().contentHash())
                || item.getAudioObjectKey() != null) {
            outboxTransactionService.succeed(work.event().id(), LocalDateTime.now());
            return;
        }
        var generated = metadata(item);
        ListeningDailySet set = item.getDailySet();
        boolean canCorrect = generated != null && generated.qualityCorrectionCount() == 0
                && work.request().durationDemand() != null && Double.isFinite(measuredSeconds)
                && measuredSeconds > 0 && item.getReplacementSequence() == 0;
        int missing = 0;
        for (int index = 1; index <= set.getTargetItemCount(); index++) {
            if (!itemRepository.existsByDailySetIdAndItemIndex(set.getId(), index)) {
                missing++;
            }
        }
        canCorrect &= set.getPhysicalItemCount() + missing
                < policySettingService.get().getHardItemLimit();
        if (canCorrect) {
            // Reserve before enqueue, in this transaction. Manual retry/restart
            // cannot reset the quality budget or enqueue a second source version.
            item.reserveDurationCorrection(jsonCodec.write(
                    ListeningDurationPolicy.withDuration(generated, work.request().durationDemand(), 1)));
            item.markNotEvaluable(code);
            var correction = new AiListeningContract.DurationCorrection(item.getSourceText(), measuredSeconds, 1);
            outboxCommandService.enqueue(ListeningOutboxType.GENERATE_SET, set.getId(),
                    new ListeningGenerationCommand(item.getId(), item.getItemIndex(),
                            item.getReplacementSequence() + 1, 0, correction),
                    "listening:set:" + set.getId() + ":duration-correction:" + item.getItemIndex());
        } else {
            item.markNotEvaluable(code);
            set.fail(code);
        }
        outboxTransactionService.succeed(work.event().id(), LocalDateTime.now());
        refreshSetState(set);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recordFailure(
            ListeningOutboxTransactionService.ClaimedEvent event,
            String reason,
            boolean retryable,
            Duration retryAfter
    ) {
        recordFailure(event, reason, retryable, retryAfter, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recordFailure(
            ListeningOutboxTransactionService.ClaimedEvent event,
            String reason,
            boolean retryable,
            Duration retryAfter,
            String failureCode
    ) {
        ListeningItem item = lockedItem(event.aggregateId());
        if (!outboxTransactionService.ownsClaim(event)) {
            return;
        }
        if (item.getStatus() != ListeningItemStatus.TTS_PENDING) {
            outboxTransactionService.succeed(event.id(), LocalDateTime.now());
            return;
        }
        boolean infrastructureFailure = failureCode != null
                && SOURCE_INDEPENDENT_FAILURES.contains(failureCode);
        String safeReason = infrastructureFailure ? failureCode : reason;
        var result = outboxTransactionService.fail(event, safeReason, retryable,
                retryAfter, policySettingService.get().getAutomaticRetryLimit(),
                LocalDateTime.now());

        if (retryable && !result.exhausted()) {
            item.registerAutomaticTtsRetry(safeReason);
        }

        if (!result.exhausted()) {
            return;
        }

        if (infrastructureFailure) {
            // Changing valid source text cannot repair quota, timeout or availability.
            // Preserve it for the existing bounded manual TTS retry instead.
            item.markNotEvaluable(safeReason);
            item.getDailySet().fail(safeReason);
            refreshSetState(item.getDailySet());
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
        ListeningPolicySnapshot policy = policySettingService.get();
        var generated = metadata(item);
        if (item.getReplacementSequence() >= 1
                || (generated != null && generated.qualityCorrectionCount() > 0)) {
            // Persisted source lineage bounds legacy replacement after any failure.
            // Neither worker restart nor manual TTS retry grants a third version.
            set.fail(reason == null || reason.isBlank() ? "LISTENING_AUDIO_FAILED" : reason);
            refreshSetState(set);
            return;
        }

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

    private AiListeningContract.GeneratedItem metadata(ListeningItem item) {
        return jsonCodec.read(item.getGenerationMetadataJson(), AiListeningContract.GeneratedItem.class);
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
