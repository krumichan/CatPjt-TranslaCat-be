package jp.co.translacat.domain.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BoardDtos {
    private BoardDtos() {
    }

    public enum Board { ADMIN, MEMBER, ANONYMOUS }

    public enum Locale { ko, ja, en }

    public enum Sort { LATEST, POPULAR }

    public enum Reaction { LIKE, DISLIKE, NONE }

    public record Write(@Size(max = 200) String title, @NotBlank @Size(max = 12000) String content,
            @Size(max = 128) String password, @Size(max = 20) List<UUID> attachments) {
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

    public record Upload(@NotBlank @Size(max = 255) String filename, @Positive long bytes) {}

    public record Created(UUID id) {}

    public record Attachment(UUID id, String filename, long bytes) {}

    public record UploadStatus(UUID id, long partBytes, List<Integer> completedParts, boolean ready) {}

    public record SignedUrl(String url) {}

    /** 독립 게시판의 식별자와 열람·작성 정책을 반환한다. 기본 게시판의 이름은 화면에서 번역한다. */
    public record Forum(String id, Board type, String name) {
        /** 기존 기본 게시판의 글은 별도 게시판 ID 없이 유형으로 구분한다. */
        String databaseId() {
            return id.equals(type.name()) ? null : id;
        }
    }

    public record ForumWrite(@NotBlank @Size(max = 60) String name, @NotNull Board type) {}

    public record ForumLocation(String id) {}

    public record Vote(@NotNull Reaction reaction) {}

    public record Reactions(long likes, long dislikes, Reaction mine) {}

    public record Entry(UUID id, boolean locked, boolean secret, boolean mine, String author, Instant createdAt,
            Instant updatedAt, String title, String content, String translatedTitle, String translatedContent,
            boolean translating, List<Attachment> attachments, Long viewCount, Reactions reactions) {}

    public record Page(List<Entry> items, boolean hasMore) {}
}
