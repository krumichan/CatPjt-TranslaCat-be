package jp.co.translacat.domain.languagelearning.listening.audio.port;

import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;

public interface ListeningAudioStoragePort {

    void store(String objectKey, byte[] bytes, String contentType);

    ListeningAudioObject load(String objectKey, String contentType);

    void delete(String objectKey);
}
