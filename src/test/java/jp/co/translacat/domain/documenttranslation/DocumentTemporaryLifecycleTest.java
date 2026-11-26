package jp.co.translacat.domain.documenttranslation;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 문서 요청의 중단과 일회 응답이 작업 디스크 및 DB 공개 경계에 닿는지 확인한다. */
class DocumentTemporaryLifecycleTest {
    private static final long OWNER = 27L;
    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final byte[] OFFICE_SAMPLE = {7, 19, 31, 47};

    @TempDir
    Path directories;

    private DocumentTransientFiles files;
    private DocumentJobRepository repository;
    private DocumentObjectWrites accounts;
    private DocumentTranslationService service;
    private DocumentTranslationController controller;
    private UserPrincipal owner;

    // 실제 임시 저장소에 인증·DB의 제한된 응답을 연결한다.
    @BeforeEach
    void setUp() throws IOException {
        files = new DocumentTransientFiles(directories.toRealPath().resolve("document-work"),
                DocumentTranslationCapacity.DEFAULT);
        repository = mock(DocumentJobRepository.class);
        accounts = mock(DocumentObjectWrites.class);
        when(accounts.whenActive(eq(OWNER), eq(false), any())).thenReturn(true);
        when(accounts.whenActive(eq(OWNER), isNull(), any()))
                .thenAnswer(call -> call.<Supplier<?>>getArgument(2).get());
        service = new DocumentTranslationService(repository, files, accounts, Clock.fixed(NOW, ZoneOffset.UTC));
        controller = new DocumentTranslationController(service, DocumentTranslationCapacity.DEFAULT);
        owner = mock(UserPrincipal.class);
        when(owner.getId()).thenReturn(OWNER);
    }

    // 테스트 응답이 끝난 뒤 자신이 만든 잠금과 작업 공간만 정리한다.
    @AfterEach
    void closeFiles() throws IOException {
        files.close();
    }

    @Test
    void rawUploadStoresTemporarySourceButNoSourceFingerprint() throws IOException {
        // 준비
        UUID key = UUID.randomUUID();
        when(repository.findByKey(OWNER, key)).thenReturn(Optional.empty());
        when(repository.insert(any())).thenReturn(true);

        // 실행
        DocumentJob job = submit(key, OFFICE_SAMPLE);

        // 검증
        var inserted = ArgumentCaptor.forClass(DocumentJob.class);
        verify(repository).insert(inserted.capture());
        assertThat(inserted.getValue().fileName()).isEqualTo("document.xlsx");
        assertThat(inserted.getValue().inputHash()).isEqualTo("0".repeat(64));
        assertThat(Files.readAllBytes(files.source(job.id()))).isEqualTo(OFFICE_SAMPLE);
    }

    @Test
    void onlyTheCurrentExactInputCanReplayARequestKey() throws IOException {
        // 준비: 원본의 길이는 같고 내용만 다른 요청을 만든다.
        UUID key = UUID.randomUUID();
        when(repository.findByKey(OWNER, key)).thenReturn(Optional.empty());
        when(repository.insert(any())).thenReturn(true);
        DocumentJob initial = submit(key, OFFICE_SAMPLE);
        when(repository.findByKey(OWNER, key)).thenReturn(Optional.of(initial));
        byte[] different = OFFICE_SAMPLE.clone();
        different[0]++;

        // 실행 및 검증: 활성 원본을 메모리에서 확인하는 동안에만 정확한 재접수를 허용한다.
        assertThat(submit(key, OFFICE_SAMPLE).id()).isEqualTo(initial.id());
        assertThatThrownBy(() -> submit(key, different))
                .isInstanceOfSatisfying(DocumentTranslationException.class,
                        failure -> assertThat(failure.code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));

        files.deleteSource(initial.id());
        assertThatThrownBy(() -> submit(key, OFFICE_SAMPLE))
                .isInstanceOfSatisfying(DocumentTranslationException.class,
                        failure -> assertThat(failure.code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void elapsedUploadBudgetSkipsDiskAdmission() {
        // 준비
        long elapsed = System.nanoTime() - 1;

        // 실행 및 검증
        assertThatThrownBy(() -> service.submit(OWNER, UUID.randomUUID(), new ByteArrayInputStream(OFFICE_SAMPLE),
                "xlsx", "ja", "en", elapsed))
                .isInstanceOfSatisfying(DocumentTranslationException.class,
                        failure -> assertThat(failure.status()).isEqualTo(HttpStatus.REQUEST_TIMEOUT));
    }

    @Test
    void uploadIOLossDiscardsPartialSourceBeforeAdmission() throws IOException {
        // 준비 첫 조각을 읽은 다음 연결이 종료된 요청을 만든다.
        InputStream disconnected = new InputStream() {
            private boolean first = true;

            @Override
            public int read() throws IOException {
                throw new IOException("Simulated connection close");
            }

            @Override
            public int read(byte[] target, int offset, int length) throws IOException {
                if (!first) {
                    throw new IOException("Simulated connection close");
                }
                first = false;
                target[offset] = 31;
                return 1;
            }
        };

        // 실행 및 검증
        assertThatThrownBy(() -> service.submit(OWNER, UUID.randomUUID(), disconnected, "xlsx", "ja", "en", deadline()))
                .isInstanceOfSatisfying(DocumentTranslationException.class,
                        failure -> assertThat(failure.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        try (var entries = Files.list(directories.resolve("document-work"))) {
            assertThat(entries.map(Path::getFileName).map(Path::toString))
                    .noneMatch(name -> name.startsWith("job-"));
        }
    }

    @Test
    void completeDownloadClosesAndDeletesTheActuallyServedResult() throws IOException {
        // 준비
        UUID id = readyResult();
        var request = new MockHttpServletRequest("GET", "/api/v1/document-translations/" + id + "/download");
        var response = new MockHttpServletResponse();

        // 실행
        controller.download(owner, id.toString(), request, response);

        // 검증
        assertThat(response.getContentAsByteArray()).isEqualTo(OFFICE_SAMPLE);
        assertThat(response.getHeader("Content-Length")).isEqualTo(Integer.toString(OFFICE_SAMPLE.length));
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(files.hasWorkspace(id)).isFalse();
        verify(repository).claimOutput(OWNER, id, DocumentTranslationService.resultMarker(id), NOW);
        assertThatThrownBy(() -> service.download(OWNER, id))
                .isInstanceOfSatisfying(DocumentTranslationException.class,
                        failure -> assertThat(failure.status()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void failedResponseOutputStillDeletesTheClaimedResult() throws IOException {
        // 준비
        UUID id = readyResult();
        var request = new MockHttpServletRequest("GET", "/api/v1/document-translations/" + id + "/download");
        var response = new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void write(int value) throws IOException {
                        assertThat(files.hasWorkspace(id)).isFalse();
                        throw new IOException("Simulated receiver close");
                    }
                };
            }
        };

        // 실행 및 검증
        assertThatThrownBy(() -> controller.download(owner, id.toString(), request, response))
                .isInstanceOf(DocumentTranslationException.class);
        assertThat(files.hasWorkspace(id)).isFalse();
        verify(repository).claimOutput(OWNER, id, DocumentTranslationService.resultMarker(id), NOW);
    }

    @Test
    void failedDiskRemovalDoesNotExposeSuccessfulResponseBytes() throws IOException {
        // 준비: 원래 내용이 있는 결과를 읽되 디스크 정리에서 IOException을 주입한다.
        UUID id = readyResult();
        var deletionFailure = spy(files);
        doReturn(new ByteArrayInputStream(OFFICE_SAMPLE)).when(deletionFailure).openResult(id);
        doThrow(new IOException("Simulated removal failure")).when(deletionFailure).discard(id);
        var rejecting = new DocumentTranslationController(new DocumentTranslationService(
                repository, deletionFailure, accounts, Clock.fixed(NOW, ZoneOffset.UTC)),
                DocumentTranslationCapacity.DEFAULT);
        var request = new MockHttpServletRequest("GET", "/api/v1/document-translations/" + id + "/download");
        var response = new MockHttpServletResponse();

        // 실행 및 검증: 파일이 남아 있는 동안에는 본문이나 성공 헤더를 송신하지 않는다.
        assertThatThrownBy(() -> rejecting.download(owner, id.toString(), request, response))
                .isInstanceOfSatisfying(DocumentTranslationException.class,
                        failure -> assertThat(failure.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(response.isCommitted()).isFalse();
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(Files.isRegularFile(files.result(id))).isTrue();
    }

    @Test
    void staleMetadataDoesNotOverrideConfirmedDiskRemoval() throws IOException {
        // 준비: 파일은 정상 정리되지만 수령 후 DB 참조 정리가 재시도되는 경우다.
        UUID id = readyResult();
        doThrow(new DataAccessResourceFailureException("Simulated metadata outage"))
                .when(repository).forgetWorkspace(OWNER, id, NOW);
        var request = new MockHttpServletRequest("GET", "/api/v1/document-translations/" + id + "/download");
        var response = new MockHttpServletResponse();

        // 실행 및 검증: 일회 수령과 파일 부재까지 확정되면 응답을 보낸다.
        controller.download(owner, id.toString(), request, response);
        assertThat(files.hasWorkspace(id)).isFalse();
        assertThat(response.getContentAsByteArray()).isEqualTo(OFFICE_SAMPLE);
        verify(repository).claimOutput(OWNER, id, DocumentTranslationService.resultMarker(id), NOW);
    }

    // 검사 실패에서 기존 오류 코드와 파일 삭제 후 참조 해제 순서를 확인한다.
    @Test
    void validationFailureClearsReferenceAfterSourceRemoval() throws Exception {
        // 준비: 실제 접수한 파일을 검사 단계에서 거부한다.
        when(repository.insert(any())).thenReturn(true);
        DocumentJob job = submit(UUID.randomUUID(), OFFICE_SAMPLE);
        when(repository.claim(NOW)).thenReturn(Optional.of(job), Optional.empty());
        when(repository.canRun(job, DocumentJob.State.VALIDATING, NOW)).thenReturn(true);
        var formats = mock(DocumentFormatPort.class);
        when(formats.read(files.source(job.id()), "xlsx")).thenThrow(new IOException("Invalid fixture"));
        var removedWhenForgotten = new CompletableFuture<Boolean>();
        doAnswer(call -> {
            removedWhenForgotten.complete(!files.hasWorkspace(job.id()));
            return null;
        }).when(repository).forgetWorkspace(OWNER, job.id(), NOW);

        // 실행: 공용 스케줄러가 사용하는 경로로 워커에 작업을 넘긴다.
        try (var worker = cleanupWorker(formats)) {
            worker.dispatch();

            // 검증: DB 참조에 손대는 순간 이미 파일이 사라졌고 분류된 오류가 보존된다.
            assertThat(removedWhenForgotten.get(5, TimeUnit.SECONDS)).isTrue();
            verify(repository).fail(job, "INVALID_PACKAGE", NOW);
        }
    }

    // 만료 파일 삭제와 DB 조회·갱신의 재시도가 독립적으로 끝나는지 확인한다.
    @Test
    void expiryReclaimsFilesWhileMetadataReadAndWriteRecover() throws IOException {
        // 준비: 첫 조회와 첫 참조 갱신을 각각 한 번 실패시킨다.
        UUID id = readyResult();
        Instant expired = NOW.plusSeconds(121);
        var expiredJob = new DocumentJobRepository.WorkspaceCleanup(id, OWNER, DocumentJob.State.EXPIRED);
        when(repository.findWorkspaceCleanup(isNull(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("Simulated metadata read outage"))
                .thenReturn(List.of(expiredJob));
        doThrow(new DataAccessResourceFailureException("Simulated metadata write outage")).doNothing()
                .when(repository).forgetWorkspace(OWNER, id, expired);

        try (var worker = cleanupWorker(mock(DocumentFormatPort.class))) {
            // 실행 및 검증: 조회 실패 중에도 파일을 지운 뒤 후속 순회에서 갱신을 다시 시도한다.
            worker.cleanWorkspaces(expired);
            assertThat(files.hasWorkspace(id)).isFalse();
            verify(repository, never()).forgetWorkspace(OWNER, id, expired);
            worker.cleanWorkspaces(expired);
            worker.cleanWorkspaces(expired);
            verify(repository, times(2)).forgetWorkspace(OWNER, id, expired);
        }
    }

    // 실제 삭제가 불가능한 동안 참조를 보존하고 경로가 복구되면 회수하는지 확인한다.
    @Test
    void terminalCleanupKeepsReferenceWhenFileRemovalFails() throws IOException {
        // 준비: 저장소가 삭제하지 않는 예외 파일을 종료된 작업 공간에 둔다.
        UUID id = UUID.randomUUID();
        Path workspace = files.create(id, OFFICE_SAMPLE.length);
        Files.write(files.source(id), OFFICE_SAMPLE);
        Path unexpected = Files.createFile(workspace.resolve("unexpected"));
        var failedJob = new DocumentJobRepository.WorkspaceCleanup(id, OWNER, DocumentJob.State.FAILED);
        when(repository.findWorkspaceCleanup(isNull(), anyInt())).thenReturn(List.of(failedJob));

        try (var worker = cleanupWorker(mock(DocumentFormatPort.class))) {
            // 실행 및 검증: 삭제 실패 뒤에는 참조를 보존하고 다음 순회에서 실제 부재를 확인한다.
            worker.cleanWorkspaces(NOW);
            assertThat(files.hasWorkspace(id)).isTrue();
            verify(repository, never()).forgetWorkspace(OWNER, id, NOW);
            Files.delete(unexpected);
            worker.cleanWorkspaces(NOW);
            assertThat(files.hasWorkspace(id)).isFalse();
            verify(repository).forgetWorkspace(OWNER, id, NOW);
        }
    }

    // 수령권 확정과 파일 열기 사이 또는 사용권 보유 중에는 정리기가 전송을 가로막지 않는다.
    @Test
    void claimedResultRemainsAvailableUntilTheDownloadCloses() throws IOException {
        // 준비: DB에서 일회 수령권을 이미 가져간 작업으로 보이게 한다.
        UUID id = readyResult();
        var claimedJob = new DocumentJobRepository.WorkspaceCleanup(id, OWNER, DocumentJob.State.SUCCEEDED);
        when(repository.findWorkspaceCleanup(isNull(), anyInt())).thenReturn(List.of(claimedJob));

        try (var worker = cleanupWorker(mock(DocumentFormatPort.class))) {
            // 실행: 첫 순회 직후 열 수 있고 두 번째 순회가 읽고 있는 결과를 유지한다.
            worker.cleanWorkspaces(NOW);
            try (var result = files.openResult(id)) {
                worker.cleanWorkspaces(NOW);
                assertThat(result.readAllBytes()).isEqualTo(OFFICE_SAMPLE);
                verify(repository, never()).forgetWorkspace(OWNER, id, NOW);
            }

            // 검증: 다운로드가 닫혀 파일이 사라진 이후에만 남은 참조를 정리한다.
            worker.cleanWorkspaces(NOW);
            assertThat(files.hasWorkspace(id)).isFalse();
            verify(repository).forgetWorkspace(OWNER, id, NOW);
        }
    }

    @Test
    void accountClosedDuringResultReadReceivesNoBytes() throws IOException {
        // 준비: 파일을 처음 열 때만 계정이 활성 상태다.
        UUID id = readyResult();
        when(accounts.whenActive(eq(OWNER), eq(false), any())).thenReturn(true, false);
        var request = new MockHttpServletRequest("GET", "/api/v1/document-translations/" + id + "/download");
        var response = new MockHttpServletResponse();

        // 실행 및 검증: 최종 계정 확인에서 차단되며 물리 정리는 끝난 상태다.
        assertThatThrownBy(() -> controller.download(owner, id.toString(), request, response))
                .isInstanceOfSatisfying(DocumentTranslationException.class,
                        failure -> assertThat(failure.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(files.hasWorkspace(id)).isFalse();
    }

    @Test
    void speculativeRequestsDoNotClaimTheOneTimeResult() {
        // 준비
        String id = UUID.randomUUID().toString();
        var head = new MockHttpServletRequest("HEAD", "/api/v1/document-translations/" + id + "/download");
        var range = new MockHttpServletRequest("GET", head.getRequestURI());
        range.addHeader("Range", "bytes=0-1");

        // 실행 및 검증
        assertThatThrownBy(() -> controller.download(owner, id, head, new MockHttpServletResponse()))
                .isInstanceOfSatisfying(DocumentTranslationException.class,
                        failure -> assertThat(failure.status()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED));
        assertThatThrownBy(() -> controller.download(owner, id, range, new MockHttpServletResponse()))
                .isInstanceOfSatisfying(DocumentTranslationException.class, failure ->
                        assertThat(failure.status()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE));
        verifyNoInteractions(repository);
    }

    // 실제 작업 공간을 사용하고 외부 번역 요청 없이 실패·정리 경로를 실행한다.
    private DocumentTranslationWorker cleanupWorker(DocumentFormatPort formats) {
        return new DocumentTranslationWorker(repository, files, formats, mock(DocumentTranslationGateway.class),
                Clock.fixed(NOW, ZoneOffset.UTC), DocumentTranslationCapacity.DEFAULT);
    }

    // 재패키징 직후처럼 결과 하나만 수령 가능한 소유 작업을 준비한다.
    private UUID readyResult() throws IOException {
        UUID id = UUID.randomUUID();
        files.create(id, OFFICE_SAMPLE.length);
        files.retainUntil(id, NOW.plusSeconds(120));
        Files.write(files.result(id), OFFICE_SAMPLE);
        var job = new DocumentJob(id, OWNER, null, UUID.randomUUID(), "request-shape", "xlsx", "document.xlsx",
                "ja", "en", DocumentJob.State.SUCCEEDED, DocumentTranslationService.inputMarker(id),
                "0".repeat(64), 35, DocumentTranslationService.resultMarker(id), (long) OFFICE_SAMPLE.length,
                null, 1, 1, List.of(), null, "gpt-5.4-mini-2026-03-17", null, 0, NOW, NOW.plusSeconds(120));
        when(repository.findOwned(OWNER, id)).thenReturn(Optional.of(job));
        when(repository.claimOutput(OWNER, id, DocumentTranslationService.resultMarker(id), NOW)).thenReturn(true);
        return id;
    }

    // 현재 테스트의 원본 바이트를 제한 시간 안에서 접수한다.
    private DocumentJob submit(UUID key, byte[] body) {
        return service.submit(OWNER, key, new ByteArrayInputStream(body), "xlsx", "ja", "en", deadline());
    }

    // 테스트는 실제 경과 시간을 기다리지 않고 유효한 업로드 기한을 만든다.
    private static long deadline() {
        return System.nanoTime() + DocumentTranslationService.TRANSFER_NANOS;
    }
}
