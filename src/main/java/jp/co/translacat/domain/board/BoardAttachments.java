package jp.co.translacat.domain.board;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.model.Part;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static jp.co.translacat.domain.board.BoardDtos.*;

@Slf4j
@Service
@RequiredArgsConstructor
class BoardAttachments {

    static final long MEMBER_MAX = 30_000_000;
    static final long R2_MAX = (5L << 40) - (5L << 30);
    private static final long MIB = 1L << 20;

    private static final RowMapper<StoredUpload> UPLOAD = (rs, index) -> new StoredUpload(
            UUID.fromString(rs.getString("id")), rs.getLong("bytes"), rs.getLong("part_bytes"),
            rs.getString("upload_id"), rs.getString("state"), rs.getBoolean("expired"));

    private final JdbcTemplate jdbc;
    private final BoardStorage storage;
    private final PlatformTransactionManager transactions;

    UploadStatus start(Board board, long owner, Upload request) {
        // 업로드 ID를 만들기 전에 게시판의 바이트 한도와 파일명을 확인한다.
        long partBytes = partBytes(board, request.bytes());
        String filename = request.filename().replaceAll("[\\p{Cntrl}/\\\\]", "_").strip();
        if (filename.isEmpty()) {
            throw BoardException.invalid();
        }

        // 실패한 DB 저장 뒤에는 더 이상 사용할 수 없는 R2 업로드를 정리한다.
        UUID id = UUID.randomUUID();
        String uploadId = storage.start(id, filename);
        try {
            jdbc.update("""
                    INSERT INTO board_attachment
                        (id, board, owner_id, filename, bytes, part_bytes, upload_id, state, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', UTC_TIMESTAMP(6) + INTERVAL 6 DAY)
                    """, id.toString(), board.name(), owner, filename, request.bytes(), partBytes, uploadId);
        } catch (RuntimeException error) {
            try {
                storage.delete(id, uploadId);
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }

        return new UploadStatus(id, partBytes, List.of(), false);
    }

    @Transactional
    UploadStatus status(Board board, long owner, UUID id) {
        StoredUpload upload = owned(board, owner, id);
        if (upload.ready()) {
            return new UploadStatus(id, upload.partBytes(), List.of(), true);
        }

        // 완료 응답이 끊긴 경우 저장된 객체를 확인하고 같은 업로드를 복구한다.
        if (verifiedObject(upload)) {
            markReady(id);
            return new UploadStatus(id, upload.partBytes(), List.of(), true);
        }

        List<Integer> completed = storage.parts(id, upload.uploadId()).stream()
                .filter(part -> validPart(upload, part))
                .map(Part::partNumber)
                .toList();
        return new UploadStatus(id, upload.partBytes(), completed, false);
    }

    @Transactional
    SignedUrl part(Board board, long owner, UUID id, int number) {
        StoredUpload upload = owned(board, owner, id);
        if (upload.ready() || number < 1 || number > partCount(upload)) {
            throw BoardException.invalid();
        }

        return new SignedUrl(storage.signPart(id, upload.uploadId(), number, partLength(upload, number)));
    }

    @Transactional
    Created complete(Board board, long owner, UUID id) {
        StoredUpload upload = owned(board, owner, id);
        if (upload.ready()) {
            return new Created(id);
        }

        // 실제 파트 개수와 바이트를 R2에서 확인한 뒤 완료한다.
        if (!verifiedObject(upload)) {
            List<Part> parts = storage.parts(id, upload.uploadId()).stream()
                    .sorted(Comparator.comparingInt(Part::partNumber))
                    .toList();
            if (parts.size() != partCount(upload)
                    || parts.stream().map(Part::partNumber).distinct().count() != parts.size()
                    || parts.stream().anyMatch(part -> !validPart(upload, part))) {
                throw BoardException.invalid();
            }
            storage.complete(id, upload.uploadId(), parts);
        }

        if (!verifiedObject(upload)) {
            throw BoardException.storage();
        }
        markReady(id);
        return new Created(id);
    }

    void attach(Board board, long owner, UUID entry, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        // 제출한 모든 파일을 현재 작성자와 게시판에 묶는다. 하나라도 잘못되면 글 저장도 롤백한다.
        if (ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size()) {
            throw BoardException.invalid();
        }

        int linkedCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM board_attachment WHERE entry_id = ? AND state = 'ATTACHED'
                """, Integer.class, entry.toString());
        if (linkedCount + ids.size() > 20) {
            throw BoardException.invalid();
        }

        for (UUID id : ids.stream().sorted().toList()) {
            int linked = jdbc.update("""
                    UPDATE board_attachment SET entry_id = ?, state = 'ATTACHED'
                    WHERE id = ? AND owner_id = ? AND board = ? AND state = 'READY'
                      AND expires_at > UTC_TIMESTAMP(6)
                    """, entry.toString(), id.toString(), owner, board.name());
            if (linked != 1) {
                throw BoardException.invalid();
            }
        }
    }

    List<Attachment> list(UUID entry) {
        return jdbc.query("""
                SELECT id, filename, bytes FROM board_attachment
                WHERE entry_id = ? AND state = 'ATTACHED' ORDER BY id
                """, (rs, index) -> new Attachment(UUID.fromString(rs.getString("id")),
                rs.getString("filename"), rs.getLong("bytes")), entry.toString());
    }

    SignedUrl download(UUID entry, UUID id) {
        boolean linked = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM board_attachment
                    WHERE id = ? AND entry_id = ? AND state = 'ATTACHED')
                """, Boolean.class, id.toString(), entry.toString()));
        if (!linked) {
            throw BoardException.missing();
        }

        return new SignedUrl(storage.download(id));
    }

    void remove(UUID entry) {
        jdbc.update("""
                UPDATE board_attachment SET state = 'DELETE', entry_id = NULL, expires_at = UTC_TIMESTAMP(6)
                WHERE entry_id = ? AND state = 'ATTACHED'
                """, entry.toString());
    }

    @Scheduled(fixedDelayString = "${translacat.board.storage.cleanup-interval-ms:60000}",
            initialDelayString = "60000")
    void cleanExpired() {
        // 후보를 찾는 동안 다른 첨부의 업로드나 글 등록을 잠그지 않는다.
        List<String> expired = jdbc.queryForList("""
                SELECT id FROM board_attachment
                WHERE state IN ('OPEN', 'READY', 'DELETE') AND expires_at <= UTC_TIMESTAMP(6)
                ORDER BY expires_at LIMIT 10
                """, String.class);
        var transaction = new TransactionTemplate(transactions);
        for (String id : expired) {
            transaction.executeWithoutResult(status -> cleanOne(id));
        }
    }

    private void cleanOne(String id) {
        // 업로드 완료·글 등록과 같은 행에서 상태를 다시 확인한다.
        var expired = jdbc.query("""
                SELECT a.*, TRUE AS expired FROM board_attachment a
                WHERE id = ? AND state IN ('OPEN', 'READY', 'DELETE') AND expires_at <= UTC_TIMESTAMP(6)
                FOR UPDATE
                """, UPLOAD, id);
        if (expired.isEmpty()) {
            return;
        }

        StoredUpload upload = expired.getFirst();
        try {
            storage.delete(upload.id(), upload.uploadId());
            jdbc.update("DELETE FROM board_attachment WHERE id = ?", id);
        } catch (BoardException failure) {
            // 실패한 외부 정리는 기록을 유지해 다시 시도한다.
            log.warn("Board attachment cleanup will retry ({})", failure.getMessage());
            jdbc.update("""
                    UPDATE board_attachment SET state = 'DELETE', expires_at = UTC_TIMESTAMP(6) + INTERVAL 1 HOUR
                    WHERE id = ?
                    """, id);
        }
    }

    static long partBytes(Board board, long bytes) {
        long limit = board == Board.ADMIN ? R2_MAX : MEMBER_MAX;
        if (bytes < 1 || bytes > limit) {
            throw BoardException.invalid();
        }

        // R2 최대 크기도 10,000개 이하의 동일 크기 파트로 분할한다.
        long minBytes = (bytes + 9999) / 10000;
        return Math.max(32L * MIB, ((minBytes + MIB - 1) / MIB) * MIB);
    }

    private StoredUpload owned(Board board, long owner, UUID id) {
        return jdbc.query("""
                SELECT a.*, expires_at <= UTC_TIMESTAMP(6) AS expired FROM board_attachment a
                WHERE id = ? AND board = ? AND owner_id = ? AND state IN ('OPEN', 'READY') FOR UPDATE
                """, UPLOAD, id.toString(), board.name(), owner).stream()
                .filter(upload -> !upload.expired()).findFirst().orElseThrow(BoardException::missing);
    }

    private boolean verifiedObject(StoredUpload upload) {
        Long bytes = storage.bytes(upload.id());
        if (bytes == null) {
            return false;
        }

        if (bytes != upload.bytes()) {
            throw BoardException.invalid();
        }
        return true;
    }

    private void markReady(UUID id) {
        jdbc.update("""
                UPDATE board_attachment SET state = 'READY', expires_at = UTC_TIMESTAMP(6) + INTERVAL 1 DAY
                WHERE id = ?
                """, id.toString());
    }

    private static int partCount(StoredUpload upload) {
        return (int) ((upload.bytes() + upload.partBytes() - 1) / upload.partBytes());
    }

    private static long partLength(StoredUpload upload, int part) {
        long remaining = upload.bytes() - (part - 1L) * upload.partBytes();
        return Math.min(remaining, upload.partBytes());
    }

    private static boolean validPart(StoredUpload upload, Part part) {
        int number = part.partNumber();
        return number >= 1 && number <= partCount(upload) && part.size() == partLength(upload, number);
    }

    private record StoredUpload(UUID id, long bytes, long partBytes,
                                String uploadId, String state, boolean expired) {
        boolean ready() {
            return "READY".equals(state);
        }
    }
}
