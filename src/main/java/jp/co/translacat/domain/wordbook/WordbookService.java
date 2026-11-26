package jp.co.translacat.domain.wordbook;

import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import jp.co.translacat.domain.wordbook.WordbookTypes.CreateResult;
import jp.co.translacat.domain.wordbook.WordbookTypes.Draft;
import jp.co.translacat.domain.wordbook.WordbookTypes.Entry;
import jp.co.translacat.domain.wordbook.WordbookTypes.LanguagePair;
import jp.co.translacat.domain.wordbook.WordbookTypes.Page;
import jp.co.translacat.domain.wordbook.WordbookTypes.PageAnchor;
import jp.co.translacat.domain.wordbook.WordbookTypes.Replay;
import jp.co.translacat.domain.wordbook.WordbookTypes.SourceKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** 계정별 저장·수정·복습 조회와 생성 재전송을 단어장 트랜잭션 안에서 처리한다. */
@Service
class WordbookService {
    private static final Duration REPLAY_WINDOW = Duration.ofHours(24);
    private final WordbookStore store;
    private final WordbookCursor cursor;
    private final AccountDataLifecycleAccess accounts;

    /** 소유 데이터 저장소와 계정 생명주기 계약을 연결한다. */
    WordbookService(WordbookStore store, WordbookCursor cursor, AccountDataLifecycleAccess accounts) {
        this.store = store;
        this.cursor = cursor;
        this.accounts = accounts;
    }

    /** 같은 계정과 키의 재전송을 복구하거나 새 항목을 저장한다. */
    @Transactional
    CreateResult create(long ownerId, UUID key, Draft draft, SourceKind kind) {
        // 탈퇴 접수·동일 표현 등록과 경쟁해도 소유자 행을 먼저 얻고 한 트랜잭션에서 판정한다.
        requireActive(ownerId);
        Instant now = now();
        String requestHash = WordbookInput.requestHash(draft, kind);
        Replay replay = store.findReplay(ownerId, key).orElse(null);
        if (replay != null && replay.expiresAt().isAfter(now)) {
            if (!replay.requestHash().equals(requestHash)) {
                throw new WordbookProblem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
            }
            return new CreateResult(findOwned(ownerId, replay.entryId()), replay.duplicate());
        }
        if (replay != null) {
            store.deleteReplay(ownerId, key);
        }

        // 표현이 있으면 저장 당시 값을 유지한다. 신규 삽입과 키 기록은 같이 커밋한다.
        String pairHash = WordbookInput.pairHash(draft);
        Entry entry = existingPair(ownerId, draft, pairHash);
        boolean duplicate = entry != null;
        if (!duplicate) {
            entry = new Entry(UUID.randomUUID(), draft.languages().sourceLanguage(), draft.languages().targetLanguage(),
                    draft.sourceText(), draft.targetText(), draft.context(), draft.sourceLabel(), kind, 1, now, now);
            store.insert(ownerId, entry, pairHash);
        }
        store.recordReplay(ownerId, key, new Replay(requestHash, entry.id(), duplicate, now.plus(REPLAY_WINDOW)));
        return new CreateResult(entry, duplicate);
    }

    /** 필터를 적용하고 동일 범위의 페이지 위치에서 조회를 계속한다. */
    @Transactional
    Page list(long ownerId, LanguagePair languages, int limit, String token) {
        // 비활성 계정의 이미 발행된 자격으로 삭제 대기 데이터를 읽는 경우까지 막는다.
        requireActive(ownerId);
        PageAnchor anchor = cursor.decode(ownerId, languages, token);

        List<Entry> entries = store.list(ownerId, languages, anchor, limit + 1);
        if (entries.size() <= limit) {
            return new Page(entries, null);
        }
        Entry last = entries.get(limit - 1);
        String next = cursor.encode(ownerId, languages, new PageAnchor(last.createdAt(), last.id()));
        return new Page(List.copyOf(entries.subList(0, limit)), next);
    }

    /** 자신의 항목 현재 버전만 읽는다. */
    @Transactional
    Entry get(long ownerId, UUID entryId) {
        requireActive(ownerId);
        return findOwned(ownerId, entryId);
    }

    /** 다른 항목과 중복되지 않는 변경을 요청 버전에만 적용한다. */
    @Transactional
    Entry replace(long ownerId, UUID entryId, long expectedVersion, Draft draft) {
        // 유효한 요청 버전을 확인한 뒤에만 저장 범위의 표현 유일 조건을 검사한다.
        requireActive(ownerId);
        requireVersion(findOwned(ownerId, entryId), expectedVersion);
        String pairHash = WordbookInput.pairHash(draft);
        Entry duplicate = existingPair(ownerId, draft, pairHash);
        if (duplicate != null && !duplicate.id().equals(entryId)) {
            throw new WordbookProblem(HttpStatus.CONFLICT, "DUPLICATE_ENTRY", duplicate.id());
        }

        // 원래 출처와 생성 시각을 유지하고 다음 버전을 확정한다.
        if (!store.replace(ownerId, entryId, expectedVersion, draft, pairHash, now())) {
            throw staleVersion();
        }
        return findOwned(ownerId, entryId);
    }

    /** 다른 계정과 이미 삭제된 항목에는 같은 응답을 반환한다. */
    @Transactional
    void delete(long ownerId, UUID entryId, long expectedVersion) {
        // 계정 상태와 버전을 잠금 범위에서 확인한다. 삭제는 기존 생성 키를 되살리지 않는다.
        requireActive(ownerId);
        requireVersion(findOwned(ownerId, entryId), expectedVersion);

        if (!store.delete(ownerId, entryId, expectedVersion)) {
            throw staleVersion();
        }
    }

    // 비교 해시가 같은 경우에도 원문을 대조해 상이한 표현을 덮어쓰지 않는다.
    private Entry existingPair(long ownerId, Draft draft, String hash) {
        Entry entry = store.findByPair(ownerId, draft.languages(), hash).orElse(null);
        if (entry != null
                && (!WordbookInput.pairText(entry.sourceText()).equals(WordbookInput.pairText(draft.sourceText()))
                        || !WordbookInput.pairText(entry.targetText()).equals(
                                WordbookInput.pairText(draft.targetText())))) {
            throw WordbookProblem.unavailable();
        }
        return entry;
    }

    // 같은 데이터베이스에서 탈퇴 접수와 순서를 정하고, 상태가 바뀌면 인증 오류를 반환한다.
    private void requireActive(long ownerId) {
        if (!accounts.lockActive(ownerId)) {
            throw new WordbookProblem(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
    }

    // 호출자 소유 범위 밖의 ID는 없는 ID와 동일하게 처리한다.
    private Entry findOwned(long ownerId, UUID entryId) {
        return store.find(ownerId, entryId).orElseThrow(WordbookProblem::missing);
    }

    // 버전 확인을 먼저 수행해 오래된 편집의 충돌 상세를 계산하지 않는다.
    private void requireVersion(Entry entry, long expected) {
        if (entry.version() != expected) {
            throw staleVersion();
        }
    }

    private WordbookProblem staleVersion() {
        return new WordbookProblem(HttpStatus.PRECONDITION_FAILED, "STALE_VERSION");
    }

    // DB의 DATETIME(6)과 응답의 저장 시각이 같도록 커밋 전에 자른다.
    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
