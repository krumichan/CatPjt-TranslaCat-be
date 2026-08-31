package jp.co.translacat.domain.languagelearning.level.pool.audio.port;

import jp.co.translacat.domain.languagelearning.level.pool.audio.model.LevelTestReferenceAudioUpload;

import java.time.Duration;

public interface LevelTestReferenceAudioUploadPort {

    LevelTestReferenceAudioUpload prepare(
            String objectKey,
            String contentType,
            Duration ttl
    );

    boolean exists(String objectKey);

    void delete(String objectKey);
}
