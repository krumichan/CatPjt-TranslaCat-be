package jp.co.translacat.domain.wordbook;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/** 단어장 처리 결과에 대응하는 HTTP 오류와 선택적 중복 항목 ID다. */
final class WordbookProblem extends RuntimeException {
    private final HttpStatus status;
    private final UUID existingEntryId;

    /** HTTP 상태와 공개 가능한 오류 코드로 생성한다. */
    WordbookProblem(HttpStatus status, String code) {
        this(status, code, null);
    }

    /** 편집 충돌에는 호출자 소유의 기존 항목 ID를 함께 담는다. */
    WordbookProblem(HttpStatus status, String code, UUID existingEntryId) {
        super(code);
        this.status = status;
        this.existingEntryId = existingEntryId;
    }

    HttpStatus status() {
        return status;
    }

    UUID existingEntryId() {
        return existingEntryId;
    }

    static WordbookProblem invalid() {
        return new WordbookProblem(HttpStatus.BAD_REQUEST, "INVALID_INPUT");
    }

    static WordbookProblem missing() {
        return new WordbookProblem(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    static WordbookProblem unavailable() {
        return new WordbookProblem(HttpStatus.SERVICE_UNAVAILABLE, "TEMPORARILY_UNAVAILABLE");
    }
}
