package jp.co.translacat.domain.languagelearning.daily.port;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;

import java.time.LocalDate;
import java.util.Optional;

/** 외부 Writing 계약을 유지하며 LL 소유 상태를 조회·변경하는 경계다. */
public interface DailyWritingGateway {
    DailyWritingSetResponseDto get(Long userId, Long setId);
    Optional<DailyWritingSetResponseDto> findByDate(Long userId, LocalDate date, DailyWritingType type);
    DailyWritingSetResponseDto create(Long userId, DailyWritingType writingType);
    DailyWritingSetResponseDto retry(Long userId, Long setId);
    DailyWritingSetResponseDto regenerate(Long userId, Long setId);
    AnswerResultResponseDto submit(Long userId, Long itemId, AnswerSubmitRequestDto request);
    void resume(Long userId, Long itemId);
}
