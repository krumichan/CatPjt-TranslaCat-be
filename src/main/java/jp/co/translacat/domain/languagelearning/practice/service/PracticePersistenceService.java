package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeGeneratedQuestionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.entity.VocabularyMastery;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeQuestionRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.practice.repository.VocabularyMasteryRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PracticePersistenceService {
    private final PracticeSetRepository setRepository;
    private final PracticeQuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final VocabularyMasteryRepository masteryRepository;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional
    public PracticeSet persist(
            Long userId,
            LocalDate learningDate,
            String originLanguage,
            String learningLanguage,
            PracticeDomain domain,
            String mode,
            int questionCount,
            int complexityBand,
            AiPracticeGenerationResponseDto generated
    ) {
        var existing = setRepository.findByUserIdAndLearningDateAndDomainAndMode(
                userId, learningDate, domain, mode
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = userRepository.getReferenceById(userId);
        PracticeSet set = setRepository.save(PracticeSet.create(
                user,
                learningDate,
                domain,
                mode,
                originLanguage,
                learningLanguage,
                questionCount,
                complexityBand
        ));
        set.markGenerated(generated.promptVersion());

        for (PracticeGeneratedQuestionDto item : generated.questions()) {
            questionRepository.save(PracticeQuestion.create(
                    set,
                    item,
                    jsonCodec.write(item.options()),
                    jsonCodec.write(item.correctAnswer()),
                    jsonCodec.write(item.vocabularyCandidates() == null ? java.util.List.of() : item.vocabularyCandidates())
            ));
            if (domain == PracticeDomain.VOCABULARY
                    && item.canonicalKey() != null
                    && !item.canonicalKey().isBlank()) {
                VocabularyMastery mastery = masteryRepository
                        .findByUserIdAndCanonicalKey(userId, item.canonicalKey())
                        .orElseGet(() -> masteryRepository.save(
                                VocabularyMastery.create(user, item.canonicalKey(), item.targetExpression())
                        ));
                mastery.markSelected(learningDate);
            }
        }
        return set;
    }
}
