package jp.co.translacat.domain.languagelearning.practice.port;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.*;

import java.util.List;

/**
 * Core는 인증된 사용자와 요청만 전달하고 Practice 업무 상태는 LL이 소유한다.
 */
public interface PracticeGateway {
    PracticeSetResponseDto today(Long userId, PracticeDomain domain, String mode);

    PracticeSetResponseDto get(Long userId, Long setId);

    PracticeSetResponseDto retry(Long userId, Long setId);

    List<PracticeTodayModeStatusResponseDto> statuses(Long userId, PracticeDomain domain);

    List<PracticeModeAvailabilityResponseDto> availability(Long userId);

    PracticeAnswerResultResponseDto answer(Long userId, Long questionId, PracticeAnswerSubmitRequestDto request);

    VocabularyMasterySummaryResponseDto mastery(Long userId);
}
