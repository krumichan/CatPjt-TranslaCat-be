package jp.co.translacat.domain.languagelearning.speaking.coaching.repository;

import jp.co.translacat.domain.languagelearning.speaking.coaching.entity.SpeakingCoachingResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SpeakingCoachingResultRepository extends JpaRepository<SpeakingCoachingResult, Long> {
    Optional<SpeakingCoachingResult> findBySessionId(Long sessionId);

    List<SpeakingCoachingResult> findAllBySessionUserIdAndSessionLearningLanguageAndSessionLearningDateBetweenAndResultPolicyVersionOrderByIdDesc(
            Long userId, String learningLanguage, LocalDate from, LocalDate to, String resultPolicyVersion);
}
