package jp.co.translacat.domain.languagelearning.speaking.port;

import jp.co.translacat.domain.languagelearning.speaking.audio.model.SpeakingAudioObject;

import java.util.List;

/**
 * 외부 Speaking DTO와 인증된 사용자만 전달하며 업무 상태·정책은 LL에서 처리한다.
 */
public interface SpeakingGateway {
    <T> T get(Long userId, String path, Class<T> type);

    <T> T optional(Long userId, String path, Class<T> type);

    <T> List<T> list(Long userId, String path, Class<T> type);

    <T> T post(Long userId, String path, Object body, Class<T> type);

    <T> T adminPatch(Long adminUserId, String path, Object body, Class<T> type);

    SpeakingAudioObject audio(Long userId, String path);
}
