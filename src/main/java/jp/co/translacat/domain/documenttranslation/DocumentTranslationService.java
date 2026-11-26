package jp.co.translacat.domain.documenttranslation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 인증된 문서 작업과 한 번만 수령할 수 있는 로컬 결과를 관리한다. */
final class DocumentTranslationService {
    static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    static final long JOB_SECONDS = 180;
    static final long RESULT_SECONDS = 120;
    static final long TRANSFER_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentTranslationService.class);
    private static final Set<String> FORMATS = Set.of("pptx", "docx", "xlsx");
    private static final Set<String> LANGUAGES = Set.of("ja", "ko", "en");

    private final DocumentJobRepository repository;
    private final DocumentTransientFiles files;
    private final DocumentObjectWrites writes;
    private final Clock clock;

    // 파일은 작업 전용 로컬 공간에만 쓰고 기존 계정 삭제 잠금 계약을 재사용한다.
    DocumentTranslationService(DocumentJobRepository repository, DocumentTransientFiles files,
            DocumentObjectWrites writes, Clock clock) {
        this.repository = repository;
        this.files = files;
        this.writes = writes;
        this.clock = clock;
    }

    // 외부 연결에서는 제한된 메모리만 사용하고, 계정 잠금 아래에서 완료된 원본을 접수한다.
    DocumentJob submit(long owner, UUID key, InputStream input, String format, String source, String target,
            long deadline) {
        requireActive(owner);
        if (format == null || !FORMATS.contains(format)) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FORMAT");
        }
        String language = source == null ? "auto" : source;
        if (target == null || !LANGUAGES.contains(target) || !("auto".equals(language) || LANGUAGES.contains(language))
                || target.equals(language)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_LANGUAGE_PAIR");
        }

        // 연결 중단 또는 계정 삭제 중에는 네트워크 수신이 파일이나 계정 잠금을 보유하지 않는다.
        UUID id = UUID.randomUUID();
        boolean accepted = false;
        try {
            byte[] body = receive(input, deadline);
            String requestShape = requestShape(format, language, target, body.length);
            String fingerprint = HexFormat.of().formatHex(DocumentFiles.sha256().digest(body));

            // 원본 쓰기·작업 등록과 계정 삭제 승인은 같은 소유자 잠금 아래에서 순서를 정한다.
            DocumentJob persisted = writes.whenActive(owner, null, () -> admit(
                    owner, id, key, format, language, target, body, requestShape, fingerprint));
            if (persisted == null) {
                throw error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND");
            }
            accepted = id.equals(persisted.id());
            return persisted;
        } catch (IOException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        } finally {
            if (!accepted) {
                discardWorkspace(owner, id);
            }
        }
    }

    // 완전히 수신한 입력만 계정이 활성 상태인 같은 트랜잭션에서 파일과 작업으로 등록한다.
    private DocumentJob admit(long owner, UUID id, UUID key, String format, String source, String target,
            byte[] body, String requestShape, String fingerprint) {
        Optional<DocumentJob> previous = repository.findByKey(owner, key);
        if (previous.isPresent()) {
            DocumentJob prior = previous.get();
            if (!prior.requestHash().equals(requestShape) || !files.matchesSource(prior.id(), fingerprint)) {
                throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT");
            }
            return prior;
        }

        // 디스크 쓰기의 범위는 이미 제한된 바이트 사본 하나이며 네트워크 I/O를 기다리지 않는다.
        try {
            Instant now = clock.instant();
            Instant expiry = now.plusSeconds(JOB_SECONDS);
            files.create(id);
            files.retainUntil(id, expiry);
            try (var lease = files.use(id)) {
                Files.write(files.source(id), body, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
                files.identifySource(id, fingerprint);
                DocumentJob job = queued(id, owner, key, requestShape, format, source, target, body.length, now, expiry);
                if (!repository.insert(job)) {
                    throw error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND");
                }
                return job;
            }
        } catch (DuplicateKeyException duplicate) {
            throw error(HttpStatus.TOO_MANY_REQUESTS, "ACTIVE_JOB_LIMIT");
        } catch (IOException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        }
    }

    // 요청자가 소유하고 계정 삭제로 차단되지 않은 작업만 조회한다.
    DocumentJob get(long owner, UUID id) {
        return repository.findOwned(owner, id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
    }

    // 대기 작업과 미수령 결과는 즉시 정리하고 실행 중인 작업은 처리기에 취소를 전한다.
    DocumentJob cancel(long owner, UUID id) {
        Instant now = clock.instant();
        repository.requestCancel(owner, id, now);
        DocumentJob current = get(owner, id);
        if (current.state() == DocumentJob.State.CANCELLED) {
            if (inputMarker(id).equals(current.inputKey())) {
                discardWorkspace(owner, id);
            }
        } else if (current.state() != DocumentJob.State.CANCELLING || !current.expiresAt().isAfter(now)) {
            throw error(HttpStatus.CONFLICT, "INVALID_JOB_STATE");
        }
        return current;
    }

    // 소유자가 수령을 확정하면 제한된 응답 메모리로만 가져오고 네트워크 전송 전 파일을 정리한다.
    Download download(long owner, UUID id) {
        DocumentJob job = get(owner, id);
        Instant now = clock.instant();
        if (!job.expiresAt().isAfter(now) || job.state() == DocumentJob.State.EXPIRED
                || job.state() == DocumentJob.State.SUCCEEDED && job.outputKey() == null) {
            throw error(HttpStatus.GONE, "FILE_EXPIRED");
        }
        if (job.state() != DocumentJob.State.SUCCEEDED || !resultMarker(id).equals(job.outputKey())
                || job.outputBytes() == null || job.outputBytes() <= 0 || job.outputBytes() > MAX_UPLOAD_BYTES) {
            throw error(HttpStatus.CONFLICT, "INVALID_JOB_STATE");
        }

        // 조회 이후 취소·만료·다른 다운로드가 이겼다면 파일 사용권조차 얻지 않는다.
        if (!repository.claimOutput(owner, id, resultMarker(id), clock.instant())) {
            throw error(HttpStatus.GONE, "FILE_EXPIRED");
        }
        boolean removed = false;
        try {
            requireActive(owner);
            byte[] content;
            try (var stream = files.openResult(id)) {
                content = stream.readNBytes(Math.toIntExact(job.outputBytes() + 1));
                if (content.length != job.outputBytes()) {
                    throw error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
                }
            }

            // 실제 작업 공간이 사라졌음을 확인한 경우에만 응답에 바이트를 넘긴다.
            files.discard(id);
            if (files.hasWorkspace(id)) {
                throw new IOException("Document result cleanup did not complete");
            }
            removed = true;
            forgetWorkspace(owner, id);

            // 로컬 복사 중 계정 삭제가 접수되었다면 파일은 지우고 응답은 제공하지 않는다.
            requireActive(owner);
            String fileName = "document." + job.targetLanguage() + "." + job.format();
            return new Download(content, fileName, job.format());
        } catch (NoSuchFileException missing) {
            throw error(HttpStatus.GONE, "FILE_EXPIRED");
        } catch (IOException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        } finally {
            if (!removed) {
                discardWorkspace(owner, id);
            }
        }
    }

    // 일회성 파일 생성 또는 정리가 끝난 작업의 로컬 참조를 없앤다.
    void discardWorkspace(long owner, UUID id) {
        try {
            files.discard(id);
            if (!files.hasWorkspace(id)) {
                forgetWorkspace(owner, id);
            }
        } catch (IOException unavailable) {
            LOGGER.warn("Document transient cleanup deferred jobId={}", id);
        }
    }

    // 이미 끝난 파일 삭제와 별개로, DB가 잠시 응답하지 않는 경우 메타데이터 정리만 다시 시도한다.
    private void forgetWorkspace(long owner, UUID id) {
        try {
            repository.forgetWorkspace(owner, id, clock.instant());
        } catch (DataAccessException unavailable) {
            LOGGER.warn("Document workspace metadata cleanup deferred jobId={}", id);
        }
    }

    // 선언된 길이와 무관하게 10 MiB 및 수신 기한을 적용하고 소켓 대기 중 작업 파일을 만들지 않는다.
    private static byte[] receive(InputStream input, long deadline) throws IOException {
        var body = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        while (System.nanoTime() - deadline < 0) {
            int count = input.read(buffer);
            if (System.nanoTime() - deadline >= 0) {
                break;
            }
            if (count == -1) {
                if (body.size() == 0) {
                    throw error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
                }
                return body.toByteArray();
            }
            if (body.size() + count > MAX_UPLOAD_BYTES) {
                throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
            }
            body.write(buffer, 0, count);
        }
        throw error(HttpStatus.REQUEST_TIMEOUT, "TRANSFER_TIMEOUT");
    }

    // 멱등 충돌 확인에는 문서 내용이나 파일명에 의존하지 않는 요청 형태만 쓴다.
    private static String requestShape(String format, String source, String target, long size) {
        String description = "transient-v1:" + format + ":" + source + ":" + target + ":" + size;
        byte[] digest = DocumentFiles.sha256().digest(description.getBytes(StandardCharsets.US_ASCII));
        return HexFormat.of().formatHex(digest);
    }

    // 신규 작업 행에 사용할 범용 이름과 파일 내용이 없는 임시 작업 식별자를 정의한다.
    private static DocumentJob queued(UUID id, long owner, UUID key, String requestShape, String format,
            String source, String target, long size, Instant now, Instant expiry) {
        return new DocumentJob(id, owner, null, key, requestShape, format, "document." + format, source, target,
                DocumentJob.State.QUEUED, inputMarker(id), "0".repeat(64), size, null, null, null, null, 0,
                List.of(), null, null, null, 0, now, expiry);
    }

    // 탈퇴가 진행 중인 계정은 로컬 파일 생성과 수령 모두를 차단한다.
    private void requireActive(long owner) {
        if (!Boolean.TRUE.equals(writes.whenActive(owner, false, () -> true))) {
            throw error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND");
        }
    }

    static String inputMarker(UUID id) {
        return "ephemeral-" + id;
    }

    static String resultMarker(UUID id) {
        return "ephemeral-result-" + id;
    }

    // 문서 응답에는 공급자나 파일시스템 오류의 원문을 포함하지 않는다.
    private static DocumentTranslationException error(HttpStatus status, String code) {
        return new DocumentTranslationException(status, code);
    }

    /** DB에 재공개할 수 없는 현재 응답의 제한된 메모리 사본이다. */
    record Download(byte[] content, String fileName, String format) {}
}
