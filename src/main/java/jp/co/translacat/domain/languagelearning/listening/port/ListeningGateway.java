package jp.co.translacat.domain.languagelearning.listening.port;

import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;

import java.util.List;

/**
 * 외부 Controller DTO를 보존하면서 Listening 업무 요청을 LL에 전달하는 경계다.
 */
public interface ListeningGateway {
    <T> T get(Long userId, String path, Class<T> type);

    <T> List<T> list(Long userId, String path, Class<T> type);

    <T> T post(Long userId, String path, Object body, Class<T> type);

    ListeningAudioObject audio(Long userId, String path);
}
