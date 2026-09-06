package jp.co.translacat.domain.languagelearning.listening.daily.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
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
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningGenerationTransactionService {

    private final ListeningDailySetRepository dailySetRepository;
    private final ListeningItemRepository itemRepository;
    private final ListeningPolicySettingQueryService policySettingService;
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
        ListeningPolicySetting policy = policySettingService.get();
        ListeningGenerationCommand command = jsonCodec.read(
                event.payloadJson(),
                ListeningGenerationCommand.class
        );
        int expectedCount = command.replacement()
                ? 1
                : set.getTargetItemCount();

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
                                1.0,
                                (double) policy.getReferenceAudioMaxSeconds(),
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
                        diversityContextService.context(
                                set.getUser().getId(),
                                set.getLearningLanguage(),
                                LanguageLearningContentSource.LISTENING
                        ),
                        GenerationFingerprintCommandService.POLICY_VERSION
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
        ListeningDailySet set = dailySetRepository.findById(
                work.event().aggregateId()
        ).orElseThrow();

        if (!work.command().replacement()
                && itemRepository.countByDailySetId(set.getId()) > 0) {
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
        }

        for (AiListeningContract.GeneratedItem generated : response.items()) {
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

            int logicalIndex = work.command().replacement()
                    ? work.command().logicalItemIndex()
                    : generated.itemIndex();
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
        outboxTransactionService.succeed(
                work.event().id(),
                java.time.LocalDateTime.now()
        );
    }

    @Transactional
    public void failPermanently(GenerationWork work, String reason) {
        ListeningDailySet set = dailySetRepository.findById(
                work.event().aggregateId()
        ).orElseThrow();

        if (!work.command().replacement()) {
            set.fail(reason);
        }
    }

    @Transactional
    public void failPermanently(Long dailySetId, String reason) {
        ListeningDailySet set = dailySetRepository.findById(dailySetId)
                .orElseThrow();

        if (set.getPhysicalItemCount() == 0 && !set.isUsable()) {
            set.fail(reason);
        }
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
