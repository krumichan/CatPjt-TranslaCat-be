package jp.co.translacat.domain.user.deletion;

import jakarta.persistence.EntityManager;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AccountDeletionService {
    private final AccountDeletionStore store;
    private final AccountDeletionReauthentication reauthentication;
    private final AccountDeletionProperties properties;
    private final AccountDeletionRemoteClients remote;
    private final AccountDeletionPersonalData personalData;
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final TransactionTemplate tx;
    private final Map<String, Window> statusAttempts = new LinkedHashMap<>();

    public AccountDeletionService(AccountDeletionStore store, AccountDeletionReauthentication reauthentication,
                                   AccountDeletionProperties properties, AccountDeletionRemoteClients remote,
                                   AccountDeletionPersonalData personalData, JdbcTemplate jdbc, EntityManager entities,
                                   PlatformTransactionManager transactions) {
        this.store = store;
        this.reauthentication = reauthentication;
        this.properties = properties;
        this.remote = remote;
        this.personalData = personalData;
        this.jdbc = jdbc;
        this.entities = entities;
        this.tx = new TransactionTemplate(transactions);
    }

    public Status request(long userId, String sid, Request request) {
        requireEnabled();
        if (request == null || !"DELETE_ACCOUNT".equals(request.confirmation())) throw DeletionTokens.invalid();
        DeletionTokens.uuid(request.operationId());
        DeletionTokens.secret(request.receiptSecret());
        DeletionTokens.secret(request.reauthenticationToken());
        if (request.expectedPublicId() == null || request.expectedPublicId().length() > 20) throw DeletionTokens.invalid();

        // 미배포/장애 downstream에서는 첫 계정 상태 변경 전에 중단한다. 호출 중에는 DB 잠금이 없다.
        remote.requireReady(userId);
        try {
            personalData.requireReady();
        } catch (RuntimeException unavailable) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_DELETION_SERVICES_UNAVAILABLE", "데이터 정리 서비스 준비 상태를 확인하고 있습니다.");
        }
        return tx.execute(status -> {
            // push 변경과 같은 registry 잠금을 먼저 사용해 늦은 PUT과 탈퇴의 역순 잠금을 피한다.
            jdbc.queryForObject("select id from push_registry_lock where id=1 for update", Integer.class);
            User user = reauthentication.locked(userId);
            reauthentication.requireSession(userId, sid);
            if (!request.expectedPublicId().equals(user.getPublicId())) {
                throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_DELETION_ACCOUNT_MISMATCH", "삭제할 계정과 로그인한 계정이 다릅니다.");
            }
            if (store.existsForUser(userId) || store.operation(request.operationId(), true).isPresent()) {
                throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_DELETION_ALREADY_REQUESTED", "이미 접수한 요청은 저장된 확인 정보로 조회해 주세요.");
            }
            var proof = store.proof(request.reauthenticationToken(), true).orElseThrow(DeletionTokens::invalid);
            reauthentication.validate(proof, userId, sid, "DELETE_ACCOUNT");

            // 상태/PII/전체 세션과 기기 연결/영속 작업 접수는 하나의 트랜잭션이다.
            Instant now = Instant.now();
            String auditIdentity = user.getEmail();
            store.create(request.operationId(), userId, request.receiptSecret(), auditIdentity, now);
            store.consume(proof, now);
            personalData.captureProfileObjects(request.operationId(), userId);
            user.requestDeletion(now);
            entities.flush();
            jdbc.update("update `user` set public_id=?,created_by='DELETED',updated_by='DELETED' where id=?", "D-" + DeletionTokens.issue().substring(0, 18), userId);
            jdbc.update("delete from push_installation where user_id=?", userId);
            jdbc.update("delete from auth_session where user_id=?", userId);
            personalData.removeAuthenticationAndProfile(userId, auditIdentity);
            return from(store.operation(request.operationId(), false).orElseThrow());
        });
    }

    public Status status(StatusRequest request, String peer) {
        if (request == null) throw DeletionTokens.invalid();
        String id = DeletionTokens.uuid(request.operationId());
        String hash = DeletionTokens.hash(DeletionTokens.secret(request.receiptSecret()));
        throttle(peer, hash);
        var operation = store.operation(id, false).orElseThrow(this::notFound);
        if (!DeletionTokens.same(hash, operation.receiptHash())
                || operation.receiptExpiresAtUtc() != null && !Instant.now().isBefore(operation.receiptExpiresAtUtc())) throw notFound();
        return from(operation);
    }

    private synchronized void throttle(String peer, String receiptHash) {
        // BFF의 공유 egress를 사용자 한 명으로 취급하지 않는다. 영수증별 한도와 넓은 peer 한도를 함께 둔다.
        Instant now = Instant.now();
        statusAttempts.entrySet().removeIf(item -> !item.getValue().expires().isAfter(now));
        reserve("P:" + DeletionTokens.hash(peer == null ? "unknown" : peer), 300, now);
        reserve("R:" + receiptHash, 30, now);
    }

    private void reserve(String key, int maximum, Instant now) {
        Window window = statusAttempts.get(key);
        if (window == null) {
            if (statusAttempts.size() >= 10000) throw limited();
            statusAttempts.put(key, new Window(now.plusSeconds(60), 1));
        } else {
            if (window.count() >= maximum) throw limited();
            statusAttempts.put(key, new Window(window.expires(), window.count() + 1));
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || !properties.isWorkerEnabled()) throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                "ACCOUNT_DELETION_UNAVAILABLE", "계정 삭제 준비 상태를 확인하고 있습니다. 잠시 후 다시 시도해 주세요.");
    }

    private AuthException notFound() { return new AuthException(HttpStatus.NOT_FOUND, "ACCOUNT_DELETION_NOT_FOUND", "삭제 확인 정보가 없거나 만료되었습니다."); }
    private AuthException limited() { return new AuthException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_DELETION_RATE_LIMITED", "잠시 후 다시 확인해 주세요.", 60); }
    private Status from(AccountDeletionStore.Operation row) { return new Status(row.operationId(), row.status(), row.requestedAtUtc(), row.completedAtUtc(), row.receiptExpiresAtUtc()); }

    public record Request(String operationId, String receiptSecret, String reauthenticationToken, String confirmation, String expectedPublicId) { }
    public record StatusRequest(String operationId, String receiptSecret) { }
    public record Status(String operationId, String status, Instant requestedAtUtc, Instant completedAtUtc, Instant receiptExpiresAtUtc) { }
    private record Window(Instant expires, int count) { }
}
