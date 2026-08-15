package jp.co.translacat.domain.languagelearning.speaking.audio.port;

import jp.co.translacat.domain.languagelearning.speaking.audio.model.SpeakingAudioObject;

public interface SpeakingAudioStoragePort {

    void store(
            String objectKey,
            byte[] bytes,
            String contentType
    );

    SpeakingAudioObject load(
            String objectKey,
            String contentType
    );

    void delete(String objectKey);
}
