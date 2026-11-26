package jp.co.translacat.domain.documenttranslation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DocumentTransientFilesTest {
    @TempDir
    private Path temporary;

    // 시작 정리는 잠금을 얻은 새 런타임의 이전 작업에만 적용된다.
    @Test
    void startupReclaimsKnownFilesWithoutTouchingUnrelatedPaths() throws IOException {
        // 준비: 전용 루트, 이전 작업, 이름이 비슷한 다른 경로를 만든다.
        Path root = root();
        try (var initialized = new DocumentTransientFiles(root)) {
            assertTrue(Files.isDirectory(root));
        }
        Path orphan = Files.createDirectory(root.resolve("job-" + UUID.randomUUID()));
        Files.writeString(orphan.resolve("source"), "synthetic source");
        Files.writeString(orphan.resolve("result"), "synthetic result");
        Files.writeString(orphan.resolve(".office-output-123.zip"), "synthetic scratch");
        Path unrelated = Files.createDirectory(root.resolve("job-other"));
        Path marker = Files.writeString(unrelated.resolve("marker"), "keep");
        UUID otherCaseId = UUID.randomUUID();
        Path otherCase = Files.createDirectory(root.resolve("JOB-" + otherCaseId));
        Path caseMarker = Files.writeString(otherCase.resolve("source"), "keep case");

        // 실행: 동일한 루트의 다음 런타임을 시작한다.
        try (var files = new DocumentTransientFiles(root)) {
            // 검증: 명확한 이전 작업만 치우고 무관한 파일을 보존한다.
            assertFalse(Files.exists(orphan));
            assertEquals("keep", Files.readString(marker));
            if (Files.exists(root.resolve("job-" + otherCaseId))) {
                assertThrows(IOException.class, () -> files.discard(otherCaseId));
            }
            assertEquals("keep case", Files.readString(caseMarker));
        }
    }

    // 다른 객체가 같은 루트를 열어도 현재 작업을 정리할 수 없다.
    @Test
    void anotherRuntimeDoesNotReclaimCurrentWork() throws IOException {
        // 준비: 잠금을 얻고 작업 원본을 만든다.
        Path root = root();
        UUID id = UUID.randomUUID();
        try (var files = new DocumentTransientFiles(root)) {
            Path job = files.create(id);
            Files.writeString(files.source(id), "active");

            // 실행 및 검증: 두 번째 소유자는 시작할 수 없다.
            assertThrows(IOException.class, () -> new DocumentTransientFiles(root));
            assertTrue(Files.isDirectory(job));
            assertEquals("active", Files.readString(files.source(id)));
        }
    }

    // 만료 검사와 취소가 진행 중인 작업자의 보유 파일을 삭제하지 않는다.
    @Test
    void expiryWaitsForTheLastWorkerLease() throws IOException {
        // 준비: 기한이 있는 작업에 두 사용권을 잡는다.
        UUID id = UUID.randomUUID();
        Instant expiry = Instant.parse("2026-11-23T12:00:00Z");
        try (var files = new DocumentTransientFiles(root())) {
            Path job = files.create(id);
            Path source = Files.writeString(files.source(id), "active");
            files.retainUntil(id, expiry);
            try (var first = files.use(id); var last = files.use(id)) {
                // 실행: DB 상태 조회 없이 만료 시점에 정리를 요청한다.
                files.reap(expiry);
                first.close();

                // 검증: 새 사용권만 막고 마지막 작업자는 파일을 읽을 수 있다.
                assertTrue(files.hasWorkspace(id));
                assertThrows(NoSuchFileException.class, () -> files.use(id));
                assertEquals("active", Files.readString(source));
            }
            assertFalse(Files.exists(job));
            assertFalse(files.hasWorkspace(id));
        }
    }

    // 결과의 단일 열기와 종료 중 잠금 유지를 검증한다.
    @Test
    void closingAnUnfinishedDownloadDiscardsFilesAndReleasesTheRoot() throws IOException {
        // 준비: 결과를 쓰고 다운로드 스트림을 열어 일부만 읽는다.
        UUID id = UUID.randomUUID();
        Path root = root();
        try (var files = new DocumentTransientFiles(root)) {
            Path job = files.create(id);
            Files.writeString(files.source(id), "discard with result");
            Files.writeString(files.result(id), "aborted result");
            assertTrue(files.hasResult(id));
            try (var input = files.openResult(id)) {
                // 실행: 두 번째 응답을 거부하고, 서버 종료 요청 후에도 현재 응답은 유지한다.
                assertEquals('a', input.read());
                assertThrows(NoSuchFileException.class, () -> files.openResult(id));
                files.close();

                // 검증: 스트림을 닫기 전에는 다른 런타임도 파일을 회수할 수 없다.
                assertFalse(files.hasResult(id));
                assertTrue(files.hasWorkspace(id));
                assertThrows(IOException.class, () -> new DocumentTransientFiles(root));
                assertTrue(Files.isDirectory(job));
            }
            assertFalse(Files.exists(job));
            try (var replacement = new DocumentTransientFiles(root)) {
                assertFalse(replacement.hasResult(id));
            }
        }
    }

    // 파일 또는 DB에 지문을 남기지 않고 같은 원본 재요청만 구분한다.
    @Test
    void sourceIdentityExpiresWithTheSource() throws IOException {
        // 준비: 한 원본과 서로 다른 검증 지문 두 개를 준비한다.
        UUID id = UUID.randomUUID();
        String first = "a".repeat(64);
        String distinct = "b".repeat(64);
        try (var files = new DocumentTransientFiles(root())) {
            files.create(id);
            Path source = Files.writeString(files.source(id), "synthetic source");
            assertFalse(files.matchesSource(id, first));

            // 실행 및 검증: 같은 지문만 다시 사용할 수 있고 다른 지문으로 바꿀 수 없다.
            files.identifySource(id, first);
            files.identifySource(id, first);
            assertTrue(files.matchesSource(id, first));
            assertFalse(files.matchesSource(id, distinct));
            assertFalse(files.matchesSource(id, "invalid"));
            assertThrows(IOException.class, () -> files.identifySource(id, distinct));

            // 실행 및 검증: 원본을 지운 뒤에는 같은 지문도 더 이상 증거가 되지 않는다.
            files.deleteSource(id);
            assertFalse(Files.exists(source));
            assertFalse(files.matchesSource(id, first));
            assertThrows(IOException.class, () -> files.identifySource(id, first));
        }
    }

    // 알 수 없는 파일을 보존하고, 실패한 정리를 이후에 다시 시도한다.
    @Test
    void cleanupFailureDoesNotReportAnAbsentWorkspace() throws IOException {
        // 준비: 정상 결과 외에 현재 정책이 삭제할 수 없는 파일을 만든다.
        UUID id = UUID.randomUUID();
        try (var files = new DocumentTransientFiles(root())) {
            Path job = files.create(id);
            Path result = Files.writeString(files.result(id), "owned");
            Path unrelated = Files.writeString(job.resolve("other"), "keep");

            // 실행 및 검증: 실패 시 결과와 무관한 파일을 남기고 새 응답은 열 수 없다.
            assertThrows(IOException.class, () -> files.discard(id));
            assertEquals("keep", Files.readString(unrelated));
            assertEquals("owned", Files.readString(result));
            assertFalse(files.hasResult(id));
            assertTrue(files.hasWorkspace(id));

            // 실행 및 검증: 충돌을 치운 뒤 재시도하면 작업만 삭제한다.
            Files.delete(unrelated);
            files.reap(Instant.now());
            assertFalse(Files.exists(job));
            assertFalse(files.hasWorkspace(id));
        }
    }

    // 파일 시스템이 허용하는 경우 외부 대상으로 향하는 결과 링크를 거부한다.
    @Test
    void resultLinkIsNeverFollowed() throws IOException {
        // 준비: 별도의 파일과 작업 내부의 연결을 만든다.
        UUID id = UUID.randomUUID();
        Path external = Files.writeString(temporary.resolve("external"), "outside");
        try (var files = new DocumentTransientFiles(root())) {
            Path job = files.create(id);
            Path link = files.result(id);
            try {
                Files.createSymbolicLink(link, external);
            } catch (IOException | UnsupportedOperationException unavailable) {
                assumeTrue(false, "Test account cannot create symbolic links");
            }

            try {
                // 실행 및 검증: 경로 조회, 응답 열기, 정리 중에도 외부 대상을 보존한다.
                assertThrows(IOException.class, () -> files.result(id));
                assertThrows(IOException.class, () -> files.openResult(id));
                assertThrows(IOException.class, () -> files.discard(id));
                assertEquals("outside", Files.readString(external));
                assertTrue(Files.exists(job));
            } finally {
                Files.deleteIfExists(link);
            }
        }
    }

    // 입력 스트림이 닫히지 않아도 결과 사용권의 정리를 한 번만 실행한다.
    @Test
    void closingFailureStillDiscardsOnceAndPreservesBothErrors() throws IOException {
        // 준비: 원본 스트림 종료와 결과 사용권 반환이 각각 실패한다.
        IOException readFailure = new IOException("synthetic input close");
        IOException cleanupFailure = new IOException("synthetic result discard");
        AtomicInteger discards = new AtomicInteger();
        InputStream broken = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public void close() throws IOException {
                throw readFailure;
            }
        };

        // 실행: 응답을 닫아 두 실패를 동시에 발생시킨다.
        try (var output = DocumentTransientFiles.discardOnClose(broken, () -> {
            discards.incrementAndGet();
            throw cleanupFailure;
        })) {
            IOException error = assertThrows(IOException.class, output::close);

            // 검증: 원래 종료 오류가 주 오류이며 정리 실패도 잃지 않는다.
            assertSame(readFailure, error);
            assertEquals(1, error.getSuppressed().length);
            assertSame(cleanupFailure, error.getSuppressed()[0]);
        }
        assertEquals(1, discards.get());
    }

    // 호스트가 임시 상위 경로에 사용하는 별칭을 테스트 루트 밖에서 해소한다.
    private Path root() throws IOException {
        return temporary.toRealPath().resolve("transient");
    }
}
