package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyWritingQueryService {

    private final DailyWritingSetRepository dailySetRepository;
    private final WritingAnswerRepository answerRepository;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final DailyWritingResponseQueryService responseQueryService;

    public DailyWritingSet findByDateOrNull(
            Long userId,
            LocalDate learningDate
    ) {
        return dailySetRepository
                .findByUserIdAndLearningDate(userId, learningDate)
                .orElse(null);
    }

    public DailyWritingSet getOwnedSet(
            Long userId,
            Long dailySetId
    ) {
        return dailySetRepository.findById(dailySetId)
                .filter(dailySet ->
                        dailySet.getUser().getId().equals(userId)
                )
                .orElseThrow(() -> new BusinessException(
                        "Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
                ));
    }

    public DailyWritingSetResponseDto getByDate(
            Long userId,
            LocalDate learningDate
    ) {
        DailyWritingSet dailySet = dailySetRepository
                .findByUserIdAndLearningDate(userId, learningDate)
                .orElseThrow(() -> new BusinessException(
                        "Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
                ));

        return toResponse(userId, dailySet);
    }

    public DailyWritingSetResponseDto toResponse(
            Long userId,
            DailyWritingSet dailySet
    ) {
        LanguageLearningUserSetting userSetting =
                userSettingQueryService.getOrCreateEntity(userId);
        LanguageLearningAdminSetting adminSetting =
                adminSettingQueryService.getOrCreateEntity();
        LocalDate today = userSettingQueryService.resolveToday(userSetting);

        return responseQueryService.toSetResponse(
                dailySet,
                today,
                adminSetting.getReviewAvailableDays()
        );
    }

    public AnswerResultResponseDto getAnswerResult(
            Long userId,
            Long answerId
    ) {
        WritingAnswer answer = answerRepository.findById(answerId)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(
                        "Writing Answer를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
                ));

        return responseQueryService.toAnswerResult(answer);
    }
}
