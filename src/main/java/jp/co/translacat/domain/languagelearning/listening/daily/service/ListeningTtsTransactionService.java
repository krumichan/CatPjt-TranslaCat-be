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
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ListeningTtsTransactionService {

    private final ListeningItemRepository itemRepository;
    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningPolicySettingQueryService policySettingService;
    private final ListeningAudioKeyFactory audioKeyFactory;
    private final ListeningOutboxCommandService outboxCommandService;
    private final ListeningOutboxTransactionService outboxTransactionService;
    private final LanguageLearningJsonCodec jsonCodec;

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
                "default",
                "v1",
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

    @Transactional
    public void apply(
            TtsWork work,
            AiListeningContract.TtsResponse response,
            String contentType
    ) {
        ListeningItem item = itemRepository.findLockedById(
                work.event().aggregateId()
        ).orElseThrow();

        if (item.getStatus() == ListeningItemStatus.READY) {
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

    @Transactional
    public void recordFailure(
            TtsWork work,
            String reason,
            boolean automaticRetry,
            boolean exhausted
    ) {
        ListeningItem item = itemRepository.findLockedById(
                work.event().aggregateId()
        ).orElseThrow();

        if (automaticRetry) {
            item.registerAutomaticTtsRetry(reason);
        }

        if (!exhausted || item.getStatus() == ListeningItemStatus.READY) {
            return;
        }

        item.markNotEvaluable(reason);
        ListeningDailySet set = item.getDailySet();
        ListeningPolicySetting policy = policySettingService.get();

        if (set.getPhysicalItemCount() < policy.getHardItemLimit()) {
            int next = item.getReplacementSequence() + 1;
            outboxCommandService.enqueue(
                    ListeningOutboxType.GENERATE_SET,
                    set.getId(),
                    new ListeningGenerationCommand(
                            item.getId(),
                            item.getItemIndex(),
                            next
                    ),
                    "listening:set:" + set.getId()
                            + ":replace:" + item.getItemIndex() + ":" + next
            );
        }

        refreshSetState(set);
    }

    private void refreshSetState(ListeningDailySet set) {
        long ready = itemRepository.countLogicalItemsByStatus(
                set.getId(),
                ListeningItemStatus.READY
        );
        set.ready(set.getGenerationVersion(), ready < set.getTargetItemCount());
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
