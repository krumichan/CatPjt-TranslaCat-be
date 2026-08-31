package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestQuestionContentPolicy;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.domain.languagelearning.level.pool.repository.LevelTestQuestionPoolRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LevelTestQuestionPoolCommandService {

    private final LevelTestQuestionPoolRepository repository;
    private final LevelTestQuestionPoolQueryService queryService;
    private final LevelTestQuestionPoolInsertService insertService;
    private final LevelTestQuestionContentPolicy contentPolicy;

    @Transactional
    public LevelTestQuestionPool register(
            String originLanguage,
            String learningLanguage,
            int generatedForQuestionNumber,
            AiLevelTestQuestionResponseDto response,
            String policyVersion,
            String modelConfigVersion
    ) {
        LevelTestQuestionContentPolicy.Health health = contentPolicy.inspect(response);
        if (!health.valid()) {
            throw new BusinessException(
                    "Level Test Pool에 저장할 수 없는 문항입니다. reason=" + health.reason(),
                    LanguageLearningErrorCode.AI_SCHEMA_INVALID
            );
        }
        var metadata = response.diversityMetadata();
        LevelTestQuestionPool duplicate = queryService.findDuplicate(
                originLanguage,
                learningLanguage,
                metadata.contentHash(),
                metadata.similarityKey()
        ).orElse(null);
        if (duplicate != null) {
            if (!duplicate.isActive()) {
                throw new BusinessException(
                        "격리된 Level Test 문항과 동일한 콘텐츠는 다시 저장할 수 없습니다.",
                        LanguageLearningErrorCode.AI_SCHEMA_INVALID
                );
            }
            attachReferenceAudioIfMissing(duplicate, response);
            return duplicate;
        }

        try {
            return insertService.insert(
                    originLanguage,
                    learningLanguage,
                    generatedForQuestionNumber,
                    response,
                    policyVersion,
                    modelConfigVersion
            );
        } catch (DataIntegrityViolationException exception) {
            LevelTestQuestionPool concurrent = queryService.findDuplicate(
                    originLanguage,
                    learningLanguage,
                    metadata.contentHash(),
                    metadata.similarityKey()
            ).orElseThrow(() -> exception);
            if (!concurrent.isActive()) {
                throw new BusinessException(
                        "격리된 Level Test 문항과 동일한 콘텐츠는 다시 저장할 수 없습니다.",
                        LanguageLearningErrorCode.AI_SCHEMA_INVALID
                );
            }
            attachReferenceAudioIfMissing(concurrent, response);
            return concurrent;
        }
    }

    private void attachReferenceAudioIfMissing(
            LevelTestQuestionPool poolQuestion,
            AiLevelTestQuestionResponseDto response
    ) {
        if (response.referenceAudio() == null) {
            return;
        }
        poolQuestion.attachReferenceAudioIfMissing(
                response.referenceAudio().objectKey(),
                response.referenceAudio().contentType(),
                response.referenceAudio().durationMs(),
                response.referenceAudio().checksumSha256()
        );
    }

    @Transactional
    public void markUsed(Long poolQuestionId) {
        repository.findById(poolQuestionId)
                .filter(LevelTestQuestionPool::isActive)
                .ifPresent(value -> value.markUsed(LocalDateTime.now()));
    }

    @Transactional
    public void quarantine(Long poolQuestionId, String reason) {
        repository.findById(poolQuestionId)
                .filter(LevelTestQuestionPool::isActive)
                .ifPresent(value -> value.quarantine(reason, LocalDateTime.now()));
    }

    @Transactional
    public void markReplaced(Long poolQuestionId, Long replacementPoolQuestionId) {
        repository.findById(poolQuestionId)
                .filter(value -> !value.isActive())
                .ifPresent(value -> value.markReplaced(replacementPoolQuestionId));
    }
}
