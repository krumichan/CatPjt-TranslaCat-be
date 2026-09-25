package jp.co.translacat.domain.languagelearning.level.port;

import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelTestStartRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.*;
import jp.co.translacat.domain.languagelearning.level.model.LevelCompletionSnapshot;
import jp.co.translacat.domain.languagelearning.level.model.LevelTestAudioData;

import java.util.List;
import java.util.Optional;

/**
 * 문항/평가/완료의 원본은 LL이다. Core의 옛 레벨 테스트 테이블을 fallback으로 읽지 않는다.
 */
public interface LevelTestGateway {
    LevelStatusResponseDto status(Long userId);

    LevelSessionResponseDto start(Long userId, LevelTestStartRequestDto request);

    LevelSessionResponseDto session(Long userId, Long sessionId);

    LevelQuestionResponseDto current(Long userId, Long sessionId);

    LevelAnswerResultResponseDto submit(Long userId, Long sessionId, Long itemId, LevelAnswerRequestDto request);

    LevelAudioAnswerResultResponseDto submitAudio(Long userId, Long sessionId, Long itemId, byte[] audio,
                                                  String contentType, Integer durationMs, String key);

    LevelAnswerResultResponseDto retry(Long userId, Long sessionId, Long itemId);

    LevelTestResultResponseDto result(Long userId, Long sessionId);

    List<LevelTestHistoryItemResponseDto> history(Long userId);

    LevelTestHistoryDetailResponseDto detail(Long userId, Long sessionId);

    LevelTestAudioData audio(Long userId, Long itemId, String kind);

    Optional<LevelCompletionSnapshot> baseline(Long userId);

    List<LevelCompletionSnapshot> completions(Long userId);
}
