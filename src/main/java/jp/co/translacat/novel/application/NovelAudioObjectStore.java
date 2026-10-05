package jp.co.translacat.novel.application;

import java.nio.file.Path;

/** 소설 음성은 기존 BE 저장소 설정 안에서 인증된 본문 재생에만 사용한다. */
public interface NovelAudioObjectStore {
    record ObjectInfo(long bytes, String sha256, String contentType) {}
    record ObjectData(byte[] bytes, String contentType) {}

    void put(String objectKey, Path file, String contentType, long bytes, String sha256);
    ObjectInfo head(String objectKey);
    ObjectData read(String objectKey, int maxBytes);

    final class Missing extends RuntimeException {
        public Missing() { super("NOVEL_AUDIO_OBJECT_MISSING"); }
    }
}
