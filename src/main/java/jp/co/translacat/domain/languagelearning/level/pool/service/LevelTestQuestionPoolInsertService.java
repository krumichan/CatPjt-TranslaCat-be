package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;
import jp.co.translacat.domain.languagelearning.level.pool.repository.LevelTestQuestionPoolRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LevelTestQuestionPoolInsertService {

    private final LevelTestQuestionPoolRepository repository;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LevelTestQuestionPool insert(
            String originLanguage,
            String learningLanguage,
            int generatedForQuestionNumber,
            AiLevelTestQuestionResponseDto response,
            String policyVersion,
            String modelConfigVersion
    ) {
        return repository.saveAndFlush(LevelTestQuestionPool.create(
                originLanguage,
                learningLanguage,
                generatedForQuestionNumber,
                response,
                jsonCodec,
                policyVersion,
                modelConfigVersion,
                LocalDateTime.now()
        ));
    }
}
