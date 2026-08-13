package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.keyword.repository.KeywordMasteryRepository;
import jp.co.translacat.domain.languagelearning.profile.dto.response.DifficultyPerformanceResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.KeywordMasteryResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.SkillScoresResponseDto;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfile;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LearningProfileQueryService {

    private static final int PROFILE_KEYWORD_LIMIT = 100;
    private static final int PROFILE_SIGNAL_LIMIT = 20;

    private final LearningProfileRepository profileRepository;
    private final KeywordMasteryRepository masteryRepository;
    private final LearningProfileSignalService signalService;
    private final UserRepository userRepository;

    @Transactional
    public LearningProfile getOrCreate(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseGet(() -> profileRepository.save(
                        LearningProfile.create(getUser(userId))
                ));
    }

    @Transactional(readOnly = true)
    public ProfileResponseDto getProfile(Long userId) {
        LearningProfile profile = profileRepository
                .findByUserId(userId)
                .orElse(null);

        if (profile == null) {
            return emptyProfile();
        }

        return toResponse(userId, profile);
    }

    private ProfileResponseDto toResponse(
            Long userId,
            LearningProfile profile
    ) {
        return new ProfileResponseDto(
                profile.getProfileVersion(),
                profile.getState(),
                profile.getBaseLevelScore(),
                profile.getCalibrationStartedDate(),
                profile.getCalibrationCompletedDate(),
                toSkillScores(profile),
                toDifficultyPerformance(profile),
                profile.getConfidence(),
                profile.getTrend(),
                getKeywordMasteries(userId),
                signalService.getResponses(
                        userId,
                        ProfileSignalType.GRAMMAR_WEAKNESS,
                        PROFILE_SIGNAL_LIMIT
                ),
                signalService.getResponses(
                        userId,
                        ProfileSignalType.ERROR_PATTERN,
                        PROFILE_SIGNAL_LIMIT
                ),
                signalService.getResponses(
                        userId,
                        ProfileSignalType.STRENGTH,
                        PROFILE_SIGNAL_LIMIT
                ),
                signalService.getResponses(
                        userId,
                        ProfileSignalType.WEAKNESS,
                        PROFILE_SIGNAL_LIMIT
                ),
                signalService.getResponses(
                        userId,
                        ProfileSignalType.RECOMMENDED_FOCUS,
                        PROFILE_SIGNAL_LIMIT
                )
        );
    }

    private List<KeywordMasteryResponseDto> getKeywordMasteries(
            Long userId
    ) {
        return masteryRepository.findAllByUserIdOrderByScoreAsc(userId)
                .stream()
                .limit(PROFILE_KEYWORD_LIMIT)
                .map(mastery -> new KeywordMasteryResponseDto(
                        mastery.getCanonicalKey(),
                        mastery.getScore(),
                        mastery.getEvaluationCount(),
                        mastery.getSelectedCount(),
                        mastery.getLastSelectedDate()
                ))
                .toList();
    }

    private SkillScoresResponseDto toSkillScores(
            LearningProfile profile
    ) {
        return new SkillScoresResponseDto(
                profile.getMeaningScore(),
                profile.getGrammarScore(),
                profile.getVocabularyScore(),
                profile.getNaturalnessScore(),
                profile.getExpressionScore()
        );
    }

    private DifficultyPerformanceResponseDto toDifficultyPerformance(
            LearningProfile profile
    ) {
        return new DifficultyPerformanceResponseDto(
                profile.getReviewPerformance(),
                profile.getNormalPerformance(),
                profile.getChallengePerformance()
        );
    }

    private ProfileResponseDto emptyProfile() {
        return new ProfileResponseDto(
                "PROFILE_V1",
                LearningProfileState.LEVEL_TEST_REQUIRED,
                null,
                null,
                null,
                new SkillScoresResponseDto(
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new DifficultyPerformanceResponseDto(
                        null,
                        null,
                        null
                ),
                0,
                "stable",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
    }
}
