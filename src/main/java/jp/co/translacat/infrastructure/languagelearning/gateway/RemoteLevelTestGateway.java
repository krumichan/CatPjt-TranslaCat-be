package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelTestStartRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.*;
import jp.co.translacat.domain.languagelearning.level.model.LevelCompletionSnapshot;
import jp.co.translacat.domain.languagelearning.level.model.LevelTestAudioData;
import jp.co.translacat.domain.languagelearning.level.port.LevelTestGateway;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningLevelTestClient;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RemoteLevelTestGateway implements LevelTestGateway {
    private final ObjectProvider<LanguageLearningLevelTestClient> clients;
    private final UserRepository users;

    public RemoteLevelTestGateway(ObjectProvider<LanguageLearningLevelTestClient> clients, UserRepository users) {
        this.clients = clients;
        this.users = users;
    }

    private LanguageLearningLevelTestClient forUser(Long userId) {
        var client = clients.getIfAvailable();
        if (client == null)
            throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE, "LL_LEVEL_REMOTE_DISABLED",
                    "레벨 테스트 연결이 비활성화되어 있습니다.");
        if (userId == null || userId <= 0 || !users.existsById(userId))
            throw new BusinessException("사용자를 찾을 수 없습니다.", LanguageLearningErrorCode.USER_NOT_FOUND);
        return client;
    }

    @Override
    public LevelStatusResponseDto status(Long userId) {
        return forUser(userId).status(userId);
    }

    @Override
    public LevelSessionResponseDto start(Long userId, LevelTestStartRequestDto request) {
        return forUser(userId).start(userId, request);
    }

    @Override
    public LevelSessionResponseDto session(Long userId, Long sessionId) {
        return forUser(userId).session(userId, sessionId);
    }

    @Override
    public LevelQuestionResponseDto current(Long userId, Long sessionId) {
        return forUser(userId).current(userId, sessionId);
    }

    @Override
    public LevelAnswerResultResponseDto submit(Long userId, Long sessionId, Long itemId,
                                               LevelAnswerRequestDto request) {
        return forUser(userId).submit(userId, sessionId, itemId, request);
    }

    @Override
    public LevelAudioAnswerResultResponseDto submitAudio(Long userId, Long sessionId, Long itemId, byte[] audio,
                                                         String contentType, Integer durationMs, String key) {
        return forUser(userId).submitAudio(userId, sessionId, itemId, audio, contentType, durationMs, key);
    }

    @Override
    public LevelAnswerResultResponseDto retry(Long userId, Long sessionId, Long itemId) {
        return forUser(userId).retry(userId, sessionId, itemId);
    }

    @Override
    public LevelTestResultResponseDto result(Long userId, Long sessionId) {
        return forUser(userId).result(userId, sessionId);
    }

    @Override
    public List<LevelTestHistoryItemResponseDto> history(Long userId) {
        return forUser(userId).history(userId);
    }

    @Override
    public LevelTestHistoryDetailResponseDto detail(Long userId, Long sessionId) {
        return forUser(userId).detail(userId, sessionId);
    }

    @Override
    public LevelTestAudioData audio(Long userId, Long itemId, String kind) {
        return forUser(userId).audio(userId, itemId, kind);
    }

    @Override
    public Optional<LevelCompletionSnapshot> baseline(Long userId) {
        return forUser(userId).baseline(userId);
    }

    @Override
    public List<LevelCompletionSnapshot> completions(Long userId) {
        return forUser(userId).completions(userId);
    }
}
