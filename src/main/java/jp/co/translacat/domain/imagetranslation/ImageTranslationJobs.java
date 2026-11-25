package jp.co.translacat.domain.imagetranslation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationAssets.hash;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.*;

@Service
@ConditionalOnProperty(prefix = "translacat.image-translation", name = "enabled", havingValue = "true")
class ImageTranslationJobs {
    static final long JOB_RESULT_TTL_MS = Duration.ofDays(7).toMillis();
    private static final long DRAFT_TTL_MS = Duration.ofHours(24).toMillis();
    private static final long ACTIVE_TTL_MS = Duration.ofHours(4).toMillis();
    private static final long UPLOAD_TTL_MS = Duration.ofMinutes(15).toMillis();
    private static final long READ_TTL_MS = Duration.ofMinutes(5).toMillis();
    private static final int OPEN_JOB_LIMIT = 5;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._~-]{1,128}");
    private static final Set<String> LANGUAGES = Set.of("ja", "ko", "en");
    private static final Set<JobStatus> CANCELLABLE = Set.of(JobStatus.AWAITING_UPLOAD, JobStatus.PREPARING,
            JobStatus.READY, JobStatus.QUEUED, JobStatus.RUNNING);

    private final ImageTranslationStore store;
    private final ImageTranslationAssets assets;
    private final ImageTranslationLinks links;
    private final ObjectMapper mapper;

    ImageTranslationJobs(ImageTranslationStore store, ImageTranslationAssets assets, ImageTranslationLinks links,
            ObjectMapper mapper) {
        this.store = store;
        this.assets = assets;
        this.links = links;
        this.mapper = mapper;
    }

    // 작업과 수락 기록을 같은 트랜잭션에 만들며, 같은 키의 동시 접수는 기존 결과로 수렴한다.
    JobView create(long owner, String key, CreateRequest request) {
        String keyHash = keyHash(key);
        String fingerprint = fingerprint(null, request);
        try {
            return view(store.transaction(() -> {
                requireActiveOwner(owner);
                Job previous = replay(owner, Operation.CREATE, keyHash, fingerprint);
                if (previous != null) {
                    return previous;
                }

                // 재전송을 먼저 처리한 뒤에만 새 파일 선언과 외부 연결 가능 여부를 검증한다.
                long now = now();
                requireOpenSlot(owner, now);
                Job job = newJob(owner, request, now);
                store.insert(job, now);
                store.accept(owner, Operation.CREATE, keyHash, fingerprint, job.id);
                return job;
            }));
        } catch (DuplicateKeyException conflict) {
            return view(replayAfterDuplicate(owner, Operation.CREATE, keyHash, fingerprint, conflict));
        }
    }

    // 일관된 회차 스냅샷을 요청 계정에게만 반환한다.
    JobView get(long owner, UUID id) {
        return view(owned(store.findReadableJob(id, owner), owner));
    }

    // 저장소 조회는 잠금 밖에서 수행하고 준비 예약은 상태와 함께 커밋한다.
    JobView prepare(long owner, UUID id, String key) {
        Job job = owned(store.findReadableJob(id, owner), owner);
        if (job.status == JobStatus.AWAITING_UPLOAD) {
            boolean uploaded = job.inputs.stream().allMatch(input -> assets.exists(job.id, input.objectId()));
            if (!uploaded) {
                throw conflict(now() >= job.uploadExpiresAt ? "UPLOAD_TICKET_EXPIRED" : "UPLOAD_INCOMPLETE");
            }
        }
        return change(owner, id, key, Operation.PREPARE, null, locked -> {
            requireStatus(locked, JobStatus.AWAITING_UPLOAD);
            locked.status = JobStatus.PREPARING;
        });
    }

    // 검토한 revision의 페이지를 요청 순서대로 고정하고 처리 대상으로 전환한다.
    JobView start(long owner, UUID id, String key, Selection request) {
        return change(owner, id, key, Operation.START, request, job -> {
            requireStatus(job, JobStatus.READY);
            validateSelection(job, request);
            List<Page> pages = new ArrayList<>();
            for (UUID pageId : request.pageIds()) {
                Page page = page(job, pageId);
                page.order = pages.size();
                pages.add(page);
            }
            job.pages = pages;
            job.status = JobStatus.QUEUED;
            job.expiresAt = now() + ACTIVE_TTL_MS;
        });
    }

    // 아직 실행을 확정하지 않은 시도의 결과 공개를 차단한다.
    JobView cancel(long owner, UUID id, String key) {
        return change(owner, id, key, Operation.CANCEL, null, job -> {
            if (!CANCELLABLE.contains(job.status)) {
                throw conflict("STATE_CONFLICT");
            }
            job.status = JobStatus.CANCELLED;
            job.expiresAt = now() + JOB_RESULT_TTL_MS;
            job.prepareLease = null;
            for (Page page : job.pages) {
                if (page.state == PageState.PENDING || page.state == PageState.RUNNING) {
                    page.state = PageState.CANCELLED;
                    page.lease = null;
                }
            }
        });
    }

    // 지정된 재시도 가능 페이지만 대기열로 되돌리고 처리 완료된 원본·결과는 보존한다.
    JobView retry(long owner, UUID id, String key, Selection request) {
        return change(owner, id, key, Operation.RETRY, request, job -> {
            if (job.status != JobStatus.PARTIAL_FAILED && job.status != JobStatus.FAILED) {
                throw conflict("STATE_CONFLICT");
            }
            validateSelection(job, request);
            requireOpenSlot(owner, now());
            for (UUID pageId : request.pageIds()) {
                Page page = page(job, pageId);
                if (page.state != PageState.FAILED || page.failure == null || !page.failure.retryable()) {
                    throw invalid("INVALID_SELECTION");
                }
                page.state = PageState.PENDING;
                page.failure = null;
                page.attempt = 0;
                page.nextRun = 0;
            }
            job.status = JobStatus.QUEUED;
            job.expiresAt = now() + ACTIVE_TTL_MS;
        });
    }

    // 원본이 준비된 선택 페이지에 대해서만 소유 계정에 읽기 주소를 발급한다.
    PageView pageView(long owner, UUID id, UUID pageId) {
        Job job = owned(store.findReadableJob(id, owner), owner);
        Page page = page(job, pageId);
        long expires = Math.min(job.expiresAt, now() + READ_TTL_MS);
        ImageInfo image = page.image;
        EngineResult result = page.result;
        String original = image == null ? null : assets.ticket("GET", job.id, page.objectId, expires);
        return new PageView(page.id, page.order, page.state, page.fileName, page.failure, original,
                original == null ? null : iso(expires), image == null ? null : image.widthPx(),
                image == null ? null : image.heightPx(), result == null ? null : result.ocrContractVersion(),
                result == null ? List.of() : result.segments());
    }

    // 업로드 주소는 원래 선언한 대상·크기·타입에만 쓸 수 있다.
    void upload(UUID id, UUID objectId, long deadline, String ticket, String contentType, InputStream body)
            throws IOException {
        if (!assets.permits("PUT", id, objectId, deadline, ticket)) {
            throw missing();
        }
        Job job = unexpired(store.findReadableJob(id, null));
        requireStatus(job, JobStatus.AWAITING_UPLOAD);
        FileRequest input = job.inputs.stream()
                .filter(item -> item.objectId().equals(objectId))
                .map(Input::declaration)
                .findFirst()
                .orElseThrow(ImageTranslationJobs::missing);
        if (!input.contentType().equals(contentType)) {
            throw new RequestFailure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        }
        try {
            assets.put(id, objectId, body, input.sizeBytes());
        } catch (IllegalArgumentException error) {
            throw invalid("UPLOAD_CONTENT_MISMATCH");
        }
    }

    // 읽기 권한은 해당 원본과 시각에 한정하고 파일명이나 외부 URL은 전달하지 않는다.
    Original original(UUID id, UUID objectId, long deadline, String ticket) throws IOException {
        if (!assets.permits("GET", id, objectId, deadline, ticket)) {
            throw missing();
        }
        Job job = unexpired(store.findReadableJob(id, null));
        Page page = job.pages.stream()
                .filter(value -> value.objectId.equals(objectId) && value.image != null)
                .findFirst()
                .orElseThrow(ImageTranslationJobs::missing);
        return new Original(page.image.contentType(), assets.open(id, objectId));
    }

    // 회차를 잠근 뒤 재전송을 먼저 판정하고, 성공한 상태 변경만 접수 이력에 남긴다.
    private JobView change(long owner, UUID id, String key, Operation operation, Object request,
            Consumer<Job> mutate) {
        String keyHash = keyHash(key);
        String fingerprint = fingerprint(id, request);
        try {
            Job updated = store.transaction(() -> {
                requireActiveOwner(owner);
                Job job = owned(store.find(id, true), owner);
                Job previous = replay(owner, operation, keyHash, fingerprint);
                if (previous != null) {
                    return previous;
                }

                // 잠근 레코드의 변경과 수락 키를 함께 영속화한다.
                mutate.accept(job);
                job.revision++;
                store.save(job, now());
                store.accept(owner, operation, keyHash, fingerprint, job.id);
                return job;
            });
            return view(updated);
        } catch (DuplicateKeyException conflict) {
            return view(replayAfterDuplicate(owner, operation, keyHash, fingerprint, conflict));
        }
    }

    // 다른 요청이 이미 키를 썼을 때 트랜잭션 롤백 후 확정 기록을 읽는다.
    private Job replayAfterDuplicate(long owner, Operation operation, String keyHash, String fingerprint,
            DuplicateKeyException conflict) {
        Job accepted = store.transaction(() -> {
            requireActiveOwner(owner);
            return replay(owner, operation, keyHash, fingerprint);
        });
        if (accepted == null) {
            throw conflict;
        }
        return accepted;
    }

    // 같은 키의 다른 본문을 거부하고 기존 회차 ID의 현재 스냅샷을 읽는다.
    private Job replay(long owner, Operation operation, String keyHash, String fingerprint) {
        var recorded = store.accepted(owner, operation, keyHash);
        if (recorded == null) {
            return null;
        }
        if (!recorded.fingerprint().equals(fingerprint)) {
            throw conflict("IDEMPOTENCY_CONFLICT");
        }
        return owned(store.find(recorded.jobId(), false), owner);
    }

    // 삭제 접수 계정은 작업 존재 여부를 드러내지 않고 변경을 거절한다.
    private void requireActiveOwner(long owner) {
        if (!store.lockActiveOwner(owner)) {
            throw missing();
        }
    }

    // 새 작업을 만들거나 닫힌 작업을 다시 열 때 모두 같은 소유자 한도를 지킨다.
    private void requireOpenSlot(long owner, long now) {
        if (store.openCount(owner, now) >= OPEN_JOB_LIMIT) {
            throw new RequestFailure(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED");
        }
    }

    // 요청 입력을 검증하고 사용자 파일명 대신 서버에서 생성한 객체 ID를 할당한다.
    private Job newJob(long owner, CreateRequest request, long now) {
        if (request == null || request.source() == null || request.source().kind() == null
                || request.sourceLanguage() == null || request.targetLanguage() == null
                || !LANGUAGES.contains(request.sourceLanguage()) || !LANGUAGES.contains(request.targetLanguage())
                || request.sourceLanguage().equals(request.targetLanguage())) {
            throw invalid("INVALID_REQUEST");
        }
        SourceRequest source = request.source();
        Job job = new Job();
        job.id = UUID.randomUUID();
        job.owner = owner;
        job.sourceKind = source.kind();
        job.sourceLanguage = request.sourceLanguage();
        job.targetLanguage = request.targetLanguage();
        job.expiresAt = now + DRAFT_TTL_MS;

        // 링크는 준비가 즉시 시작되며 파일 종류는 전송이 끝날 때까지 대기한다.
        if (source.kind() == SourceKind.LINK) {
            if (source.files() != null || source.archive() != null) {
                throw invalid("INVALID_REQUEST");
            }
            job.sourceLink = links.canonical(source.url());
            job.status = JobStatus.PREPARING;
        } else {
            job.inputs = declarations(source);
            job.status = JobStatus.AWAITING_UPLOAD;
            job.uploadExpiresAt = now + UPLOAD_TTL_MS;
        }
        return job;
    }

    // 형식별 한도를 적용하고 선언이 중복되거나 혼합되는 요청을 거절한다.
    private static List<Input> declarations(SourceRequest source) {
        boolean archive = source.kind() == SourceKind.ZIP;
        if (source.url() != null || (archive && (source.archive() == null || source.files() != null))
                || (!archive && (source.files() == null || source.archive() != null || source.files().isEmpty()))) {
            throw invalid("INVALID_REQUEST");
        }
        List<FileRequest> files = archive ? List.of(source.archive()) : source.files();
        if (files.size() > MAX_PAGES) {
            throw limitExceeded();
        }
        List<Input> inputs = new ArrayList<>();
        Set<String> fileIds = new HashSet<>();
        long total = 0;
        for (FileRequest file : files) {
            validateFile(file, archive);
            if (!fileIds.add(file.clientFileId())) {
                throw invalid("INVALID_REQUEST");
            }
            total += file.sizeBytes();
            inputs.add(new Input(UUID.randomUUID(), file));
        }
        if (total > MAX_UPLOAD_BYTES) {
            throw limitExceeded();
        }
        return inputs;
    }

    // 파일마다 식별자, 표시 이름, 매체와 정해진 바이트 한도를 검사한다.
    private static void validateFile(FileRequest file, boolean archive) {
        if (file == null || file.clientFileId() == null || !IDENTIFIER.matcher(file.clientFileId()).matches()) {
            throw invalid("INVALID_REQUEST");
        }
        if (!ImageTranslationInput.safeFileName(file.fileName())) {
            throw invalid("INVALID_FILE_NAME");
        }
        String media = file.contentType();
        if (media == null || (archive ? !"application/zip".equals(media)
                : !ImageTranslationInput.IMAGE_TYPES.contains(media))) {
            throw new RequestFailure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        }
        if (file.sizeBytes() < 1 || file.sizeBytes() > (archive ? MAX_UPLOAD_BYTES : MAX_IMAGE_BYTES)) {
            throw limitExceeded();
        }
    }

    // 선택한 페이지가 모두 현재 확정 목록에 속하고 revision이 일치하는지 검사한다.
    private static void validateSelection(Job job, Selection request) {
        if (request == null || request.pageIds() == null || request.pageIds().isEmpty()
                || request.pageIds().size() > MAX_PAGES || request.pageIds().stream().anyMatch(id -> id == null)
                || new HashSet<>(request.pageIds()).size() != request.pageIds().size()) {
            throw invalid("INVALID_SELECTION");
        }
        if (request.revision() != job.revision) {
            throw conflict("STALE_REVISION");
        }
        Set<UUID> pages = new HashSet<>(job.pages.stream().map(page -> page.id).toList());
        if (!pages.containsAll(request.pageIds())) {
            throw invalid("INVALID_SELECTION");
        }
    }

    // 전체 페이지의 상태와 공개 가능한 필드만 API DTO로 투영한다.
    private JobView view(Job job) {
        long[] counts = new long[PageState.values().length];
        List<PageSummary> pages = new ArrayList<>();
        for (Page page : job.pages) {
            counts[page.state.ordinal()]++;
            pages.add(new PageSummary(page.id, page.order, page.state, page.fileName, page.failure));
        }
        Counts progress = new Counts(counts[PageState.PENDING.ordinal()], counts[PageState.RUNNING.ordinal()],
                counts[PageState.READY.ordinal()], counts[PageState.NO_TEXT.ordinal()],
                counts[PageState.FAILED.ordinal()], counts[PageState.CANCELLED.ordinal()]);

        // 전송 주소는 대기 중이면서 티켓 기한 내일 때만 반환한다.
        List<Upload> uploads = null;
        if (job.status == JobStatus.AWAITING_UPLOAD && now() < job.uploadExpiresAt) {
            uploads = job.inputs.stream().map(input -> new Upload(input.declaration().clientFileId(), "PUT",
                    assets.ticket("PUT", job.id, input.objectId(), job.uploadExpiresAt),
                    Map.of("Content-Type", input.declaration().contentType()), iso(job.uploadExpiresAt))).toList();
        }
        return new JobView(job.id, job.sourceKind, job.sourceLanguage, job.targetLanguage, job.status,
                job.revision, iso(job.expiresAt), pages, progress, uploads, job.inputIssues, job.failure,
                job.attribution);
    }

    // 다른 계정의 작업에는 만료 여부도 공개하지 않는다.
    private static Job owned(Job job, long owner) {
        if (job == null || job.owner != owner) {
            throw missing();
        }
        return unexpired(job);
    }

    // 만료된 작업에는 새로운 자산 주소를 발급하지 않는다.
    private static Job unexpired(Job job) {
        if (job == null) {
            throw missing();
        }
        if (job.status == JobStatus.EXPIRED || job.expiresAt <= now()) {
            throw new RequestFailure(HttpStatus.GONE, "JOB_EXPIRED");
        }
        return job;
    }

    private static Page page(Job job, UUID id) {
        return job.pages.stream().filter(page -> page.id.equals(id)).findFirst()
                .orElseThrow(ImageTranslationJobs::missing);
    }

    private static void requireStatus(Job job, JobStatus status) {
        if (job.status != status) {
            throw conflict("STATE_CONFLICT");
        }
    }

    // 키를 검증한 뒤 DB collation에 의존하지 않는 해시를 만든다.
    private static String keyHash(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw invalid("INVALID_REQUEST");
        }
        return hash(value.getBytes(StandardCharsets.UTF_8));
    }

    // 같은 본문을 다른 작업 경로에 재사용해도 충돌하도록 대상 ID를 포함한다.
    private String fingerprint(UUID job, Object request) {
        try {
            return hash(mapper.writeValueAsBytes(java.util.Arrays.asList(job, request)));
        } catch (JsonProcessingException error) {
            throw invalid("INVALID_REQUEST");
        }
    }

    private static RequestFailure limitExceeded() {
        return new RequestFailure(HttpStatus.PAYLOAD_TOO_LARGE, "INPUT_LIMIT_EXCEEDED");
    }

    private static RequestFailure conflict(String code) {
        return new RequestFailure(HttpStatus.CONFLICT, code);
    }

    private static RequestFailure invalid(String code) {
        return new RequestFailure(HttpStatus.BAD_REQUEST, code);
    }

    private static RequestFailure missing() {
        return new RequestFailure(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static String iso(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    static long now() {
        return Instant.now().toEpochMilli();
    }

    record Original(String contentType, InputStream stream) {}
}
