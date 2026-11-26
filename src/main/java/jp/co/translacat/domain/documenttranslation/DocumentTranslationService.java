package jp.co.translacat.domain.documenttranslation;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 인증 주체의 문서 접수와 결과 접근 정책을 실행한다. */
final class DocumentTranslationService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long RETENTION_SECONDS = 24L * 60 * 60;
    private static final Set<String> FORMATS = Set.of("pptx", "docx", "xlsx");
    private static final Set<String> LANGUAGES = Set.of("ja", "ko", "en");

    private final DocumentJobRepository repository;
    private final DocumentFiles files;
    private final DocumentObjectWrites writes;
    private final Clock clock;

    // 공유 업무 엔티티 없이 문서 전용 영속 경계만 연결한다.
    DocumentTranslationService(DocumentJobRepository repository, DocumentFiles files, DocumentObjectWrites writes,
            Clock clock) {
        this.repository = repository;
        this.files = files;
        this.writes = writes;
        this.clock = clock;
    }

    // 파일 해시로 멱등 접수를 판별하고 비공개 저장이 성공한 원본만 작업으로 기록한다.
    DocumentJob submit(long owner, UUID key, MultipartFile file, String source, String target) {
        requireActive(owner);
        String language = source == null ? "auto" : source;
        if (target == null || !LANGUAGES.contains(target) || !("auto".equals(language) || LANGUAGES.contains(language))
                || target.equals(language)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_LANGUAGE_PAIR");
        }

        String name = safeFileName(file.getOriginalFilename());
        String format = extension(name);
        if (file.getSize() > MAX_BYTES) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        if (file.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }

        Path staged = null;
        String inputKey = null;
        UUID id = null;
        boolean referencePossible = false;
        try {
            // 해시를 계산하는 동안 실제 읽은 파일 크기를 제한한다.
            staged = Files.createTempFile("translacat-document-", ".input");
            String inputHash = stage(file, staged);
            String signature = signature("submit", inputHash, name, language, target);
            Optional<DocumentJob> previous = replay(owner, key, signature);
            if (previous.isPresent()) {
                return previous.get();
            }

            // 작업 생성 경합에 실패하는 경우 이 시도에만 속한 객체를 정리한다.
            id = UUID.randomUUID();
            inputKey = "source-" + id;
            if (!writes.store(files, owner, id, inputKey, staged, () -> true)) {
                throw error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND");
            }
            Instant now = clock.instant();
            DocumentJob next = queued(id, owner, null, key, signature, format, name, language, target,
                    inputKey, inputHash, Files.size(staged), now, now.plusSeconds(RETENTION_SECONDS));
            // DB 연결이 끊겨 커밋을 확인하지 못하면 원본을 보존한다.
            referencePossible = true;
            try {
                DocumentJob accepted = insert(next);
                referencePossible = accepted.id().equals(id);
                return accepted;
            } catch (DocumentTranslationException rejected) {
                referencePossible = false;
                throw rejected;
            }
        } catch (IOException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        } finally {
            deleteTemp(staged);
            if (!referencePossible && inputKey != null) {
                deleteObject(owner, id, inputKey);
            }
        }
    }

    // 소유자에게만 현재 상태를 보여 준다.
    DocumentJob get(long owner, UUID id) {
        return repository.findOwned(owner, id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
    }

    // 경합 후 확정된 상태를 다시 읽어 취소의 실제 결과를 응답한다.
    DocumentJob cancel(long owner, UUID id) {
        Instant now = clock.instant();
        repository.requestCancel(owner, id, now);
        DocumentJob current = get(owner, id);
        if (!current.expiresAt().isAfter(now)
                || current.state() != DocumentJob.State.CANCELLED && current.state() != DocumentJob.State.CANCELLING) {
            throw error(HttpStatus.CONFLICT, "INVALID_JOB_STATE");
        }
        return current;
    }

    // 만료 여유가 있는 원본을 공유하는 새 시도로 접수한다.
    DocumentJob retry(long owner, UUID id, UUID key) {
        String signature = signature("retry", id.toString());
        Optional<DocumentJob> previous = replay(owner, key, signature);
        if (previous.isPresent()) {
            return previous.get();
        }

        Instant now = clock.instant();
        DocumentJob prior = get(owner, id);
        if (prior.inputKey() == null || !prior.expiresAt().isAfter(now.plusSeconds(180))) {
            throw error(HttpStatus.GONE, "FILE_EXPIRED");
        }
        if (!prior.state().permitsRetry(prior.errorCode())) {
            throw error(HttpStatus.CONFLICT, "INVALID_JOB_STATE");
        }
        return insert(queued(UUID.randomUUID(), owner, id, key, signature, prior.format(), prior.fileName(),
                prior.sourceLanguage(), prior.targetLanguage(), prior.inputKey(), prior.inputHash(), prior.inputBytes(),
                now, prior.expiresAt()));
    }

    // 파일 스트림은 소유자·성공 상태·공개 기한을 확인한 후에만 열 수 있다.
    Download download(long owner, UUID id) {
        DocumentJob job = get(owner, id);
        if (!clock.instant().isBefore(job.expiresAt())) {
            throw error(HttpStatus.GONE, "FILE_EXPIRED");
        }
        if (job.state() != DocumentJob.State.SUCCEEDED || job.outputKey() == null || job.outputBytes() == null) {
            throw error(HttpStatus.CONFLICT, "INVALID_JOB_STATE");
        }

        Path verified = null;
        try {
            String name = job.fileName().substring(0, job.fileName().lastIndexOf('.'))
                    + "." + job.targetLanguage() + "." + job.format();
            // 헤더를 보내기 전에 저장한 결과의 길이와 해시를 대조한다.
            verified = Files.createTempFile("translacat-document-", ".download");
            files.copyVerified(job.outputKey(), verified, job.outputBytes(), job.outputHash());
            // 파일 검증 중에 탈퇴가 접수됐을 수 있으므로 응답 스트림을 넘기기 전에 계정 상태를 다시 확인한다.
            requireActive(owner);
            InputStream content = downloadStream(verified);
            verified = null;
            return new Download(content, name, job.format(), job.outputBytes());
        } catch (IOException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        } finally {
            deleteTemp(verified);
        }
    }

    // 응답을 완료하거나 수신이 중단되면 검증용 임시 파일을 지운다.
    private static InputStream downloadStream(Path verified) throws IOException {
        return new FilterInputStream(Files.newInputStream(verified)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    deleteTemp(verified);
                }
            }
        };
    }

    // 멱등 키가 같은 업무 의도에 쓰였는지 먼저 확인한다.
    private Optional<DocumentJob> replay(long owner, UUID key, String signature) {
        return repository.findByKey(owner, key).map(prior -> {
            if (!prior.requestHash().equals(signature)) {
                throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT");
            }
            return prior;
        });
    }

    // 키 재전송과 소유자의 다른 활성 작업을 DB 유일 제약에서 구분한다.
    private DocumentJob insert(DocumentJob job) {
        try {
            if (!repository.insert(job)) {
                throw error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND");
            }
            return job;
        } catch (DuplicateKeyException duplicate) {
            return replay(job.ownerId(), job.idempotencyKey(), job.requestHash())
                    .orElseThrow(() -> error(HttpStatus.TOO_MANY_REQUESTS, "ACTIVE_JOB_LIMIT"));
        }
    }

    // 스트리밍으로 수신 바이트 수와 원본 해시를 동시에 확인한다.
    private static String stage(MultipartFile file, Path destination) throws IOException {
        MessageDigest digest = DocumentFiles.sha256();
        long total = 0;
        try (var input = file.getInputStream(); var output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) {
                    throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
                }
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        }
        if (total == 0) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    // 다운로드 헤더에도 사용할 UTF-8 이름에서 경로와 제어 문자를 제거한다.
    private static String safeFileName(String original) {
        if (original == null) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }
        String path = original.replace('\\', '/');
        String name = Normalizer.normalize(path.substring(path.lastIndexOf('/') + 1), Normalizer.Form.NFC)
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (name.isEmpty() || name.length() > 180) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }
        return name;
    }

    // 원래 MIME 선언은 사용자 입력이므로 파일 이름과 이후 OOXML 검사로 형식을 결정한다.
    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FORMAT");
        }
        String format = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!FORMATS.contains(format)) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FORMAT");
        }
        return format;
    }

    // 길이로 각 해시 재료를 구분해 조합 경계를 보존한다.
    private static String signature(String... components) {
        MessageDigest digest = DocumentFiles.sha256();
        for (String component : components) {
            byte[] bytes = component.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    // 신규 접수 시 진행·리스 필드는 아직 비어 있다.
    private static DocumentJob queued(UUID id, long owner, UUID parent, UUID key, String signature,
            String format, String name, String source, String target, String inputKey, String inputHash,
            long inputBytes, Instant now, Instant expiry) {
        return new DocumentJob(id, owner, parent, key, signature, format, name, source, target,
                DocumentJob.State.QUEUED, inputKey, inputHash, inputBytes, null, null, null,
                null, 0, List.of(), null, null, null, 0, now, expiry);
    }

    // 파일 정리 실패 때문에 사용자 접수 결과를 변경하지 않는다.
    static void deleteTemp(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // 워커의 임시 디렉터리 수명 정책에 따라 다시 정리한다.
            }
        }
    }

    // 작업 생성에 실패한 입력은 키가 다른 작업으로 연결될 수 없다.
    private void deleteObject(long owner, UUID job, String key) {
        try {
            writes.removeOrphan(files, owner, job, key);
        } catch (IOException | RuntimeException ignored) {
            // 남아 있는 객체 쓰기 기록이 계정 삭제와 수명 정리에서 재시도 대상을 유지한다.
        }
    }

    // 인증 이후 실제 접수와 다운로드가 탈퇴로 닫혔으면 다른 계정의 작업과 같은 응답을 반환한다.
    private void requireActive(long owner) {
        if (!Boolean.TRUE.equals(writes.whenActive(owner, false, () -> true))) {
            throw error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND");
        }
    }

    // 응답 상태와 공개 코드만으로 문서 실패를 생성한다.
    private static DocumentTranslationException error(HttpStatus status, String code) {
        return new DocumentTranslationException(status, code);
    }

    record Download(InputStream content, String fileName, String format, long bytes) {}
}
