package jp.co.translacat.domain.cameratranslation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.cameratranslation.CameraOcrStore.Claim;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.Accepted;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.JobStatus;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.JobView;
import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import jp.co.translacat.domain.user.deletion.AccountDeletionDomainCleanup;
import jp.co.translacat.domain.user.deletion.AccountDeletionStore;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** 소유자가 요청한 정지 이미지 OCR의 단기 접수·취소·복구를 관리한다. */
@Service
class CameraOcrJobs implements AccountDeletionDomainCleanup {
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration RETENTION = Duration.ofMinutes(15);
    private static final String INPUT = "input";
    private static final String RESULT = "result";

    private final CameraOcrStore store;
    private final CameraTranslationAi ai;
    private final CameraOcrCipher cipher;
    private final AccountDataLifecycleAccess accounts;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    /** 각 작업 상태 전환의 짧은 트랜잭션과 외부 호출을 분리한다. */
    CameraOcrJobs(CameraOcrStore store, CameraTranslationAi ai, CameraOcrCipher cipher,
            AccountDataLifecycleAccess accounts, ObjectMapper json, PlatformTransactionManager manager) {
        this.store = store;
        this.ai = ai;
        this.cipher = cipher;
        this.accounts = accounts;
        this.json = json;
        this.transactions = new TransactionTemplate(manager);
    }

    /** 같은 접수 키를 재사용하거나 새 암호화 crop을 등록한다. */
    Accepted enqueue(long owner, UUID key, UUID imageId, String language, CameraTranslationInput.Crop crop) {
        if (!cipher.available()) {
            throw CameraTranslationProblem.unavailable();
        }
        UUID jobId = UUID.randomUUID();
        String hash = fingerprint(imageId, language, crop);
        byte[] encrypted = cipher.seal(jobId, INPUT, crop.bytes());

        // 계정 삭제와 키 중복 판정이 같은 계정 행을 통해 직렬화된다.
        return Objects.requireNonNull(transactions.execute(status -> {
            requireActive(owner);
            Instant now = Instant.now();
            var replay = store.byKey(owner, key, now);
            if (replay.isPresent()) {
                var saved = replay.get();
                if (!saved.requestHash().equals(hash)) {
                    throw new CameraTranslationProblem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
                }
                return new Accepted(saved.id(), saved.status(), saved.expiresAt());
            }
            if (!store.canEnqueue(owner, now)) {
                throw new CameraTranslationProblem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", 5);
            }

            store.deleteExpiredKey(owner, key, now);
            Instant expires = now.plus(RETENTION);
            store.insert(owner, jobId, key, hash, imageId, language, crop.contentType(), encrypted, now,
                    now.plus(JOB_TIMEOUT), expires);
            return new Accepted(jobId, JobStatus.QUEUED, expires);
        }));
    }

    /** 작업 소유자에게만 유효한 상태와 OCR 결과를 반환한다. */
    JobView get(long owner, UUID jobId) {
        return Objects.requireNonNull(transactions.execute(status -> {
            // 요청 인증 뒤에 탈퇴가 수락됐으면 이전 결과를 읽지 못하게 한다.
            requireActive(owner);
            var row = store.byId(owner, jobId, Instant.now()).orElseThrow(CameraTranslationProblem::notFound);
            JsonNode result = null;
            if (row.status() == JobStatus.SUCCEEDED) {
                try {
                    result = json.readTree(cipher.open(jobId, RESULT, row.result()));
                } catch (IOException failure) {
                    throw CameraTranslationProblem.unavailable();
                }
            }
            return new JobView(row.id(), row.status(), row.expiresAt(), result, row.error());
        }));
    }

    /** 입력과 결과를 함께 제거하고 유효한 작업의 반복 취소를 허용한다. */
    void cancel(long owner, UUID jobId) {
        transactions.executeWithoutResult(status -> {
            requireActive(owner);
            Instant now = Instant.now();
            if (!store.cancel(owner, jobId, now) && store.byId(owner, jobId, now).isEmpty()) {
                throw CameraTranslationProblem.notFound();
            }
        });
    }

    /** OCR 전용 실행기에서 만료를 정리하고 인수한 작업 하나를 처리한다. */
    @Scheduled(scheduler = "cameraOcrScheduler", fixedDelayString = "${camera-translation.poll-ms:500}",
            initialDelay = 1_000)
    public void run() {
        if (!cipher.available()) {
            return;
        }

        // 후보 조회와 인수는 짧게 유지하고 AI 호출은 임대 획득 뒤에 시작한다.
        if (!store.expireIfProvisioned(Instant.now())) {
            return;
        }
        for (var candidate : store.candidates(Instant.now())) {
            var claimed = Objects.requireNonNull(transactions.execute(status -> {
                if (!accounts.lockActive(candidate.owner())) {
                    return java.util.Optional.<Claim>empty();
                }
                return store.claim(candidate, UUID.randomUUID(), Instant.now());
            }));
            if (claimed.isPresent()) {
                process(claimed.get());
                return;
            }
        }
    }

    /** 키 제거로 새 인식이 꺼져도 기존 암호문과 상태의 만료를 계속 청소한다. */
    @Scheduled(scheduler = "cameraOcrScheduler", fixedDelay = 10_000, initialDelay = 1_000)
    public void expireWithoutKey() {
        if (!cipher.available()) {
            store.expireIfProvisioned(Instant.now());
        }
    }

    // 유효한 결과만 암호화해 넘기고 시간 안에 복구할 수 있는 일시 장애만 다시 대기시킨다.
    private void process(Claim claim) {
        Duration remaining = Duration.between(Instant.now(), claim.deadline());
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }

        byte[] result;
        try {
            byte[] image = cipher.open(claim.candidate().id(), INPUT, claim.input());
            var crop = new CameraTranslationInput.Crop(claim.contentType(), image);
            JsonNode recognized = ai.recognize(claim.imageId(), claim.language(), crop,
                    remaining.compareTo(CALL_TIMEOUT) < 0 ? remaining : CALL_TIMEOUT);
            CameraOcrResult.verify(claim.imageId(), recognized);
            byte[] encoded = json.writeValueAsBytes(recognized);
            result = cipher.seal(claim.candidate().id(), RESULT, encoded);
        } catch (CameraTranslationAi.UpstreamFailure failure) {
            handleFailure(claim, failure.kind());
            return;
        } catch (CameraTranslationProblem | JsonProcessingException failure) {
            handleFailure(claim, CameraTranslationAi.FailureKind.UNAVAILABLE);
            return;
        }

        finish(claim, JobStatus.SUCCEEDED, result, null);
    }

    // 최초 실행의 일시 장애만 같은 전체 작업 기한 안에서 다시 대기시킨다.
    private void handleFailure(Claim claim, CameraTranslationAi.FailureKind failure) {
        Instant now = Instant.now();
        if (failure != CameraTranslationAi.FailureKind.INVALID && claim.attempt() == 1
                && now.plusSeconds(1).isBefore(claim.deadline())) {
            finish(claim, JobStatus.QUEUED, null, null);
            return;
        }
        String code = switch (failure) {
            case INVALID -> "OCR_DECODE_FAILED";
            case TIMEOUT -> "OCR_DEADLINE_EXCEEDED";
            case UNAVAILABLE -> "OCR_UNAVAILABLE";
        };
        finish(claim, JobStatus.FAILED, null, code);
    }

    // 탈퇴 또는 취소 전환을 이긴 현재 실행만 상태를 기록할 수 있다.
    private void finish(Claim claim, JobStatus state, byte[] result, String error) {
        transactions.executeWithoutResult(transaction -> {
            if (accounts.lockActive(claim.candidate().owner())) {
                store.finish(claim, state, result, error, Instant.now());
            }
        });
    }

    // 인증 이후 탈퇴로 전환된 계정은 작업을 쓰지 못한다.
    private void requireActive(long owner) {
        if (!accounts.lockActive(owner)) {
            throw new CameraTranslationProblem(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
    }

    // 메타데이터와 실제 선택 바이트를 입력 지문에 묶는다.
    private static String fingerprint(UUID imageId, String language, CameraTranslationInput.Crop crop) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((imageId + ":" + language + ":" + crop.contentType() + ":")
                    .getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest.digest(crop.bytes()));
        } catch (NoSuchAlgorithmException failure) {
            throw CameraTranslationProblem.unavailable();
        }
    }

    /** 계정 정리 오케스트레이터에 카메라가 소유한 영역의 이름을 제공한다. */
    @Override
    public String domain() {
        return "CAMERA_TRANSLATION";
    }

    /** 신규 SQL 적용 전에도 기존 계정 탈퇴를 허용하며 설치 중 스키마 오류는 보고한다. */
    @Override
    public void requireReady() {
        store.provisioned();
    }

    /** 탈퇴가 수락된 계정의 작업 데이터와 재전송 기록을 같은 경계에서 제거한다. */
    @Override
    public boolean clean(AccountDeletionStore.Operation operation) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            if (!store.provisioned()) {
                return true;
            }
            if (!accounts.lockDeletionAccepted(operation.userId())) {
                return false;
            }
            store.deleteOwner(operation.userId());
            return true;
        }));
    }
}
