package jp.co.translacat.domain.languagelearning.listening.daily.service;

import com.fasterxml.jackson.core.type.TypeReference;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningDurationPolicy;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningGenerationCommand;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.setting.model.ListeningPolicySnapshot;
import jp.co.translacat.domain.languagelearning.listening.setting.port.ListeningPolicyGateway;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityHistoryItem;
import jp.co.translacat.domain.languagelearning.quality.dto.LanguageComplexityContext;
import jp.co.translacat.domain.languagelearning.quality.policy.LanguageComplexityPolicy;
import jp.co.translacat.domain.languagelearning.quality.repository.LanguageLearningGenerationFingerprintRepository;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationDiversityContextService;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationFingerprintCommandService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningGenerationTransactionService {

    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningItemRepository itemRepository;
    private final ListeningPolicyGateway policySettingService;
    private final ListeningOutboxCommandService outboxCommandService;
    private final ListeningOutboxTransactionService outboxTransactionService;
    private final LanguageLearningJsonCodec jsonCodec;
    private final GenerationDiversityContextService diversityContextService;
    private final GenerationFingerprintCommandService fingerprintCommandService;
    private final LanguageLearningGenerationFingerprintRepository fingerprintRepository;
    private final LanguageComplexityPolicy complexityPolicy;

    @Transactional(readOnly = true)
    public GenerationWork prepare(
            ListeningOutboxTransactionService.ClaimedEvent event
    ) {
        ListeningDailySet set = dailySetRepository.findById(event.aggregateId())
                .orElseThrow();
        ListeningPolicySnapshot policy = policySettingService.get();
        ListeningGenerationCommand command = jsonCodec.read(
                event.payloadJson(),
                ListeningGenerationCommand.class
        );
        // The AI always returns a local itemIndex of 1. Slot ownership belongs to BE.
        if (command.logicalItemIndex() == null) {
            command = ListeningGenerationCommand.item(1, command.manualRetryAttempt());
        }
        int expectedCount = 1;
        AiListeningContract.DurationDemand durationDemand = ListeningDurationPolicy.effective(
                set.getDifficulty(), 1.0, (double) policy.getReferenceAudioMaxSeconds());
        ListeningItem correctionSource = null;
        if (command.durationCorrection() != null) {
            ListeningItem previous = itemRepository.findById(command.replacementForItemId()).orElseThrow();
            var metadata =
                    jsonCodec.read(previous.getGenerationMetadataJson(), AiListeningContract.GeneratedItem.class);
            if (metadata == null || metadata.durationDemand() == null
                    || metadata.qualityCorrectionCount() != 1
                    || previous.getStatus() != ListeningItemStatus.NOT_EVALUABLE
                    || previous.getAudioObjectKey() != null
                    || !previous.getSourceText().equals(command.durationCorrection().previousSourceText())
                    || command.durationCorrection().qualityCorrectionCount() != 1
                    || !previous.getDailySet().getId().equals(set.getId())) {
                throw new IllegalStateException("LISTENING_DURATION_CORRECTION_AUTHORITY_INVALID");
            }
            durationDemand = metadata.durationDemand();
            correctionSource = previous;
        }

        if (set.getPhysicalItemCount() + expectedCount
                > policy.getHardItemLimit()) {
            throw new BusinessException(
                    "Listening 물리 문항 한도를 초과했습니다.",
                    LanguageLearningErrorCode.LISTENING_REPLACEMENT_LIMIT_EXCEEDED
            );
        }

        List<ListeningItem> recent = itemRepository
                .findTop200ByDailySetUserIdAndDailySetLearningLanguageOrderByCreatedAtDesc(
                        set.getUser().getId(),
                        set.getLearningLanguage()
                );
        if (correctionSource != null) {
            Long correctionId = correctionSource.getId();
            recent = recent.stream().filter(value -> !value.getId().equals(correctionId)).toList();
        }
        List<SelectedKeywordDto> keywordDtos = jsonCodec.read(
                set.getKeywordSnapshotJson(),
                new TypeReference<List<SelectedKeywordDto>>() {
                }
        );
        List<AiListeningContract.Keyword> keywords = keywordDtos.stream()
                .map(value -> new AiListeningContract.Keyword(
                        value.key(),
                        value.text(),
                        value.source(),
                        value.type(),
                        value.canonicalKey(),
                        value.selectionWeight()
                ))
                .toList();
        String requestId = "be-listening-generation-" + event.id();
        AiListeningContract.GenerationRequest request =
                new AiListeningContract.GenerationRequest(
                        requestId,
                        event.idempotencyKey(),
                        new AiListeningContract.UserContext(
                                set.getOriginLanguage(),
                                set.getLearningLanguage(),
                                set.getDifficulty(),
                                profileFocus(set.getProfileSnapshotJson())
                        ),
                        new AiListeningContract.SetContext(
                                set.getLearningDate(),
                                set.getLearningMode(),
                                new AiListeningContract.Topic(
                                        "daily",
                                        "Daily Listening"
                                ),
                                keywords,
                                expectedCount,
                                set.getDifficulty()
                        ),
                        new AiListeningContract.GenerationConstraints(
                                durationDemand.minSeconds(),
                                durationDemand.maxSeconds(),
                                recent.stream()
                                        .map(ListeningItem::getContentHash)
                                        .distinct()
                                        .limit(200)
                                        .toList(),
                                recent.stream()
                                        .map(ListeningItem::getSimilarityKey)
                                        .distinct()
                                        .limit(200)
                                        .toList()
                        ),
                        policy.getProfilePolicyVersion(),
                        policy.getModelConfigVersion(),
                        command.manualRetryAttempt(),
                        listeningComplexity(set),
                        diversityContext(set, correctionSource),
                        GenerationFingerprintCommandService.POLICY_VERSION,
                        command.durationCorrection(),
                        new AiListeningContract.Voice(set.getLearningLanguage(), "marin", "openai-speech-v1",
                                "STANDARD")
                );

        return new GenerationWork(
                event,
                command,
                request,
                expectedCount,
                policy.getReferenceAudioMaxSeconds()
        );
    }

    @Transactional
    public void apply(
            GenerationWork work,
            AiListeningContract.GenerationResponse response
    ) {
        ListeningDailySet set = dailySetRepository.findLockedById(
                work.event().aggregateId()
        ).orElseThrow();

        if (!outboxTransactionService.ownsClaim(work.event())) {
            return;
        }

        if (!work.command().replacement()
                && itemRepository.existsByDailySetIdAndItemIndex(
                set.getId(), work.command().logicalItemIndex())) {
            enqueueNextMissing(set);
            refreshAvailability(set);
            outboxTransactionService.succeed(
                    work.event().id(),
                    java.time.LocalDateTime.now()
            );
            return;
        }

        ListeningItem replaced = null;

        if (work.command().replacement()) {
            replaced = itemRepository.findLockedById(
                    work.command().replacementForItemId()
            ).orElseThrow();
            // A recovered/manual TTS result or another replacement wins over late AI.
            if (replaced.getStatus() != ListeningItemStatus.NOT_EVALUABLE
                    || !replaced.getDailySet().getId().equals(set.getId())
                    || replaced.getAudioObjectKey() != null) {
                outboxTransactionService.succeed(work.event().id(), LocalDateTime.now());
                return;
            }
        }

        int reservedSlots = work.command().replacement() ? missingSlotCount(set) : 0;
        if (set.getPhysicalItemCount() + reservedSlots
                >= policySettingService.get().getHardItemLimit()) {
            throw new BusinessException("Listening 물리 문항 한도를 초과했습니다.",
                    LanguageLearningErrorCode.LISTENING_REPLACEMENT_LIMIT_EXCEEDED);
        }

        for (AiListeningContract.GeneratedItem generated : response.items()) {
            var expectedDemand = ListeningDurationPolicy.effective(set.getDifficulty(),
                    work.request().constraints().audioSecondsMin(), work.request().constraints().audioSecondsMax());
            int correctionCount = work.command().durationCorrection() == null ? 0 : 1;
            if (generated.durationDemand() != null && !expectedDemand.equals(generated.durationDemand())) {
                throw new IllegalStateException("LISTENING_DURATION_DEMAND_MISMATCH");
            }
            if (work.command().durationCorrection() != null
                    && generated.sourceText().equals(work.command().durationCorrection().previousSourceText())) {
                throw new IllegalStateException("LISTENING_DURATION_CORRECTION_UNCHANGED");
            }
            generated = ListeningDurationPolicy.withDuration(generated, expectedDemand, correctionCount);
            if (fingerprintRepository
                    .existsByUserIdAndLearningLanguageAndContentHashAndGeneratedAtGreaterThanEqual(
                            set.getUser().getId(),
                            set.getLearningLanguage(),
                            generated.contentHash(),
                            java.time.LocalDateTime.now().minusDays(90)
                    )) {
                throw new BusinessException(
                        "최근 90일 Listening 문항과 중복된 생성 결과입니다.",
                        LanguageLearningErrorCode.AI_SCHEMA_INVALID
                );
            }

            int logicalIndex = work.command().logicalItemIndex();
            ListeningItem item = ListeningItem.create(
                    set,
                    logicalIndex,
                    generated.sourceText(),
                    generated.normalizedSourceText(),
                    jsonCodec.write(generated.referenceMeanings()),
                    jsonCodec.write(generated.keyMeaningUnits()),
                    jsonCodec.write(generated.targetKeywords()),
                    generated.estimatedAudioSeconds(),
                    generated.contentHash(),
                    generated.similarityKey(),
                    jsonCodec.write(generated),
                    work.command().replacementForItemId(),
                    work.command().replacementSequence()
            );
            item.bindPendingVoice(jsonCodec.write(work.request().referenceVoice()));
            item = itemRepository.saveAndFlush(item);
            fingerprintCommandService.register(
                    set.getUser().getId(),
                    LanguageLearningContentSource.LISTENING,
                    String.valueOf(item.getId()),
                    set.getLearningLanguage(),
                    generated.sourceText(),
                    generated.diversityMetadata()
            );
            set.recordPhysicalItem();
            outboxCommandService.enqueue(
                    ListeningOutboxType.GENERATE_TTS,
                    item.getId(),
                    null,
                    "listening:item:" + item.getId() + ":tts:0"
            );
        }

        if (replaced != null) {
            replaced.markReplaced();
        }

        set.generated(response.generationVersion());
        if (!work.command().replacement()) {
            enqueueNextMissing(set);
        }
        refreshAvailability(set);
        outboxTransactionService.succeed(
                work.event().id(),
                java.time.LocalDateTime.now()
        );
    }

    @Transactional
    public void recordFailure(
            ListeningOutboxTransactionService.ClaimedEvent event,
            String reason,
            boolean retryable,
            Duration retryAfter
    ) {
        ListeningDailySet set = dailySetRepository.findLockedById(event.aggregateId())
                .orElseThrow();
        if (!outboxTransactionService.ownsClaim(event)) {
            return;
        }
        var result = outboxTransactionService.fail(event, reason, retryable,
                retryAfter, policySettingService.get().getAutomaticRetryLimit(),
                LocalDateTime.now());
        if (!result.exhausted()) {
            return;
        }
        ListeningGenerationCommand command = jsonCodec.read(event.payloadJson(),
                ListeningGenerationCommand.class);
        int index = command.logicalItemIndex() == null ? 1 : command.logicalItemIndex();
        if (command.replacement()
                || !itemRepository.existsByDailySetIdAndItemIndex(set.getId(), index)) {
            if (!command.replacement() || set.getFailureReason() == null) {
                set.fail(reason == null || reason.isBlank()
                        ? "Listening 문항 생성에 실패했습니다." : reason);
            }
            refreshAvailability(set);
        }
    }

    private void enqueueNextMissing(ListeningDailySet set) {
        for (int index = 1; index <= set.getTargetItemCount(); index++) {
            if (!itemRepository.existsByDailySetIdAndItemIndex(set.getId(), index)) {
                outboxCommandService.enqueue(ListeningOutboxType.GENERATE_SET,
                        set.getId(), ListeningGenerationCommand.item(index, 0),
                        "listening:set:" + set.getId() + ":generate:item:" + index
                                + ":manual:0");
                return;
            }
        }
    }

    private void refreshAvailability(ListeningDailySet set) {
        set.refreshAvailability(
                itemRepository.countLogicalItemsByStatus(set.getId(), ListeningItemStatus.READY),
                itemRepository.countLogicalItemsByStatus(set.getId(), ListeningItemStatus.TTS_PENDING));
    }

    private int missingSlotCount(ListeningDailySet set) {
        int missing = 0;
        for (int index = 1; index <= set.getTargetItemCount(); index++) {
            if (!itemRepository.existsByDailySetIdAndItemIndex(set.getId(), index)) {
                missing++;
            }
        }
        return missing;
    }

    private LanguageComplexityContext listeningComplexity(ListeningDailySet set) {
        LearningProfileSummaryDto profile = jsonCodec.read(
                set.getProfileSnapshotJson(),
                LearningProfileSummaryDto.class
        );
        Double baseScore = profile == null ? null : profile.baseLevelScore();
        int baseBand = complexityPolicy.baseBand(baseScore);
        int targetBand = switch (set.getDifficulty()) {
            case EASY -> complexityPolicy.clamp(baseBand - 1);
            case MY_LEVEL -> baseBand;
            case CHALLENGE -> complexityPolicy.clamp(baseBand + 1);
        };
        return new LanguageComplexityContext(
                baseScore, baseBand, targetBand, LanguageComplexityPolicy.VERSION
        );
    }

    private DiversityContext diversityContext(ListeningDailySet set, ListeningItem correctionSource) {
        DiversityContext history = diversityContextService.context(set.getUser().getId(),
                set.getLearningLanguage(), LanguageLearningContentSource.LISTENING);
        List<DiversityHistoryItem> current = itemRepository
                .findAllByDailySetIdOrderByItemIndexAscReplacementSequenceAsc(set.getId())
                .stream().filter(item -> correctionSource == null
                        || !item.getId().equals(correctionSource.getId())).limit(40).map(item -> {
                    var generated = jsonCodec.read(item.getGenerationMetadataJson(),
                            AiListeningContract.GeneratedItem.class);
                    var metadata = generated == null ? null : generated.diversityMetadata();
                    return new DiversityHistoryItem(LanguageLearningContentSource.LISTENING,
                            item.getSourceText(), item.getContentHash(),
                            metadata == null ? null : metadata.scenarioCategory(),
                            metadata == null ? null : metadata.communicativeIntent(),
                            metadata == null ? null : metadata.taskArchetype(),
                            metadata == null ? List.of() : metadata.grammarFocusCodes(),
                            metadata == null ? null : metadata.semanticSummary(), 0);
                }).toList();
        if (correctionSource == null) {
            return new DiversityContext(current, history.sameFeatureRecent(),
                    history.crossFeatureRecent(), history.exactContentHashes90d());
        }
        // The unexposed source is being length-corrected, not offered as a new
        // exercise. Keep every other history exclusion; exact unchanged text is
        // independently rejected by both generation and apply.
        return new DiversityContext(current, history.sameFeatureRecent().stream()
                .filter(value -> !correctionSource.getContentHash().equals(value.contentHash())).toList(),
                history.crossFeatureRecent(), history.exactContentHashes90d().stream()
                .filter(hash -> !correctionSource.getContentHash().equals(hash)).toList());
    }

    private List<String> profileFocus(String snapshotJson) {
        LearningProfileSummaryDto snapshot = jsonCodec.read(
                snapshotJson,
                LearningProfileSummaryDto.class
        );

        if (snapshot == null || snapshot.recommendedFocus() == null) {
            return List.of();
        }

        return snapshot.recommendedFocus().stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(30)
                .toList();
    }

    public record GenerationWork(
            ListeningOutboxTransactionService.ClaimedEvent event,
            ListeningGenerationCommand command,
            AiListeningContract.GenerationRequest request,
            int expectedCount,
            int maxAudioSeconds
    ) {
    }
}
