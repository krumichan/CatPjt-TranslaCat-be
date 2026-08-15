package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfileEvidence;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileEvidenceRepository;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingProfileSignalDto;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpeakingProfileSignalService {

    private final LearningProfileEvidenceRepository evidenceRepository;
    private final UserRepository userRepository;

    @Transactional
    public void apply(
            Long userId,
            List<AiSpeakingProfileSignalDto> signals,
            double activityWeight
    ) {
        if (signals == null || signals.isEmpty()) {
            return;
        }
        User user = userRepository.getReferenceById(userId);
        for (AiSpeakingProfileSignalDto signal : signals) {
            if (signal == null
                    || signal.patternKey() == null
                    || signal.patternKey().isBlank()) {
                continue;
            }
            String patternKey = normalize(signal.patternKey());
            String direction = normalizeDirection(signal.direction());
            Optional<LearningProfileEvidence> existing = evidenceRepository
                    .findByUserIdAndSourceAndPatternKeyAndDirection(
                            userId,
                            LearningSource.SPEAKING,
                            patternKey,
                            direction
                    );
            if (existing.isPresent()) {
                existing.get().touch(
                        signal.metricType(),
                        signal.confidence(),
                        activityWeight,
                        signal.recommendedFocus()
                );
                continue;
            }
            evidenceRepository.save(
                    LearningProfileEvidence.create(
                            user,
                            LearningSource.SPEAKING,
                            signal.metricType(),
                            patternKey,
                            direction,
                            signal.confidence(),
                            activityWeight,
                            signal.recommendedFocus()
                    )
            );
        }
    }

    private String normalize(String value) {
        String cleaned = value.trim();
        return cleaned.substring(0, Math.min(300, cleaned.length()));
    }

    private String normalizeDirection(String value) {
        if (value == null || value.isBlank()) {
            return "WEAKNESS";
        }
        return value.trim().toUpperCase();
    }
}
