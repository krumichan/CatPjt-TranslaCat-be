package jp.co.translacat.novel.application;

import java.nio.file.Path;

/** 소설 음성은 기존 BE 저장소 설정 안에서 인증된 본문 재생에만 사용한다. */
public interface NovelAudioObjectStore {
    record ObjectInfo(long bytes, String sha256, String contentType) {}
    record ObjectData(byte[] bytes, String contentType) {}

    void put(String objectKey, Path file, String contentType, long bytes, String sha256);

    /** 객체를 저장하고 저장소가 반환한 단일 쓰기의 ETag를 전달한다. 제공되지 않으면 null을 반환한다. */
    default String putAndGetEtag(String objectKey, Path file, String contentType, long bytes, String sha256) {
        put(objectKey, file, contentType, bytes, sha256);
        return null;
    }

    ObjectInfo head(String objectKey);
    ObjectData read(String objectKey, int maxBytes);
    default void deleteAudio(String objectKey) {
        throw new UnsupportedOperationException("Audio deletion is not configured");
    }

    final class Missing extends RuntimeException {
        public Missing() {
            super("NOVEL_AUDIO_OBJECT_MISSING");
        }
    }
}
