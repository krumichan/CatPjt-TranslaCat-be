package jp.co.translacat.domain.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BoardDtos {

    private BoardDtos() {
    }

    public enum Board {
        ADMIN, MEMBER, ANONYMOUS
    }

    public enum Locale {
        ko, ja, en
    }

    public record Write(
            @Size(max = 200) String title,
            @NotBlank @Size(max = 12000) String content,
            @Size(max = 128) String password,
            @Size(max = 20) List<UUID> attachments
    ) {
        // 요청 객체를 진단 출력에 사용해도 비밀번호나 비밀 본문을 기록하지 않는다.
        @Override
        public String toString() {
            return "BoardWrite[redacted]";
        }
    }

    public record Unlock(@NotBlank @Size(max = 128) String password) {
        @Override
        public String toString() {
            return "BoardUnlock[redacted]";
        }
    }

    public record Upload(@NotBlank @Size(max = 255) String filename, @Positive long bytes) {
    }

    public record Created(UUID id) {
    }

    public record Attachment(UUID id, String filename, long bytes) {
    }

    public record UploadStatus(UUID id, long partBytes, List<Integer> completedParts, boolean ready) {
    }

    public record SignedUrl(String url) {
    }

    public record Entry(
            UUID id,
            boolean locked,
            boolean secret,
            boolean mine,
            String author,
            Instant createdAt,
            Instant updatedAt,
            String title,
            String content,
            String translatedTitle,
            String translatedContent,
            boolean translating,
            List<Attachment> attachments
    ) {
    }

    public record Page(List<Entry> items, boolean hasMore) {
    }
}
