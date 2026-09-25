package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.setting.model.AdminSettingsSnapshot;
import jp.co.translacat.domain.languagelearning.setting.model.UserSettingsSnapshot;
import jp.co.translacat.domain.languagelearning.setting.port.AdminSettingsGateway;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
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
    private final UserSettingsGateway userSettingQueryService;
    private final AdminSettingsGateway adminSettingQueryService;
    private final DailyWritingResponseQueryService responseQueryService;

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
            LocalDate learningDate,
            DailyWritingType writingType
    ) {
        DailyWritingSet dailySet = dailySetRepository
                .findByUserIdAndLearningDateAndWritingType(
                        userId,
                        learningDate,
                        writingType
                )
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
        UserSettingsSnapshot userSetting =
                userSettingQueryService.getSnapshot(userId);
        AdminSettingsSnapshot adminSetting =
                adminSettingQueryService.getSnapshot();
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
