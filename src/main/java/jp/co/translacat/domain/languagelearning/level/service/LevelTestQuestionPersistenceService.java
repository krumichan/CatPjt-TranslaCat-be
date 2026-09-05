package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;
import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionPoolCommandService;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemStatus;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityMetadata;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationFingerprintCommandService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LevelTestQuestionPersistenceService {

    private final LevelTestItemRepository itemRepository;
    private final LevelTestResponseRepository responseRepository;
    private final LanguageLearningJsonCodec jsonCodec;
    private final GenerationFingerprintCommandService fingerprintCommandService;
    private final LevelTestQuestionPoolCommandService poolCommandService;

    @Transactional
    public LevelTestItem saveGenerated(
            LevelTestSession session,
            AiLevelTestQuestionResponseDto response
    ) {
        LevelTestItem existing = itemRepository
                .findBySessionIdAndQuestionNumber(
                        session.getId(),
                        response.questionNumber()
                )
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        LevelTestItem generated = LevelTestItem.create(
                session,
                response.questionNumber(),
                response.domain(),
                response.itemType(),
                response.complexityBand(),
                response.instruction(),
                response.instructionLanguage(),
                response.answerMode(),
                response.answerLanguage(),
                response.promptText(),
                jsonCodec.write(response.options() == null ? List.of() : response.options()),
                jsonCodec.write(response.internalAnswerKey()),
                jsonCodec.write(response.referencePayload()),
                jsonCodec.write(response.diversityMetadata()),
                response.maxAnswerLength(),
                response.maxAudioSeconds(),
                response.generationVersion(),
                response.promptVersion()
        );
        if (response.referenceAudio() != null) {
            generated.attachReferenceAudio(
                    response.referenceAudio().objectKey(),
                    response.referenceAudio().contentType()
            );
        }
        LevelTestItem item = itemRepository.saveAndFlush(generated);

        fingerprintCommandService.register(
                session.getUser().getId(),
                LanguageLearningContentSource.LEVEL_TEST,
                session.getId() + ":" + item.getId(),
                session.getLearningLanguage(),
                response.promptText(),
                response.diversityMetadata()
        );
        return item;
    }

    @Transactional
    public LevelTestItem saveFromPool(
            LevelTestSession session,
            int questionNumber,
            LevelTestQuestionPool poolQuestion
    ) {
        LevelTestItem existing = itemRepository
                .findBySessionIdAndQuestionNumber(
                        session.getId(),
                        questionNumber
                )
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        LevelTestItem item = itemRepository.saveAndFlush(
                LevelTestItem.createFromPool(
                        session,
                        questionNumber,
                        poolQuestion
                )
        );

        fingerprintCommandService.register(
                session.getUser().getId(),
                LanguageLearningContentSource.LEVEL_TEST,
                session.getId() + ":" + item.getId(),
                session.getLearningLanguage(),
                poolQuestion.getPromptText(),
                jsonCodec.read(
                        poolQuestion.getDiversityMetadataJson(),
                        DiversityMetadata.class
                )
        );
        poolCommandService.markUsed(poolQuestion.getId());
        return item;
    }


    @Transactional
    public boolean discardReadyInvalidItem(Long itemId) {
        LevelTestItem item = itemRepository.findLockedById(itemId).orElse(null);
        if (item == null
                || item.getStatus() != LevelTestItemStatus.READY
                || responseRepository.findByItemId(itemId).isPresent()) {
            return false;
        }
        itemRepository.delete(item);
        itemRepository.flush();
        return true;
    }

    @Transactional
    public void attachReferenceAudio(
            Long itemId,
            String objectKey,
            String contentType
    ) {
        LevelTestItem item = itemRepository
                .findLockedById(itemId)
                .orElseThrow();
        item.attachReferenceAudio(objectKey, contentType);
    }

    @Transactional
    public void attachModelAnswerAudio(
            Long itemId,
            String objectKey,
            String contentType
    ) {
        LevelTestItem item = itemRepository
                .findLockedById(itemId)
                .orElseThrow();
        item.attachModelAnswerAudio(objectKey, contentType);
    }
}
