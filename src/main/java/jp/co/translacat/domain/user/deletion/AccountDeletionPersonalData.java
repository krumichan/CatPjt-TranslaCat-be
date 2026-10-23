package jp.co.translacat.domain.user.deletion;

import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AccountDeletionPersonalData {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<ImageStoragePort> storage;
    private final List<AccountDeletionDomainCleanup> domains;
    private final TransactionTemplate tx;

    public AccountDeletionPersonalData(JdbcTemplate jdbc, ObjectProvider<ImageStoragePort> storage,
                                       List<AccountDeletionDomainCleanup> domains, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.domains = domains;
        this.tx = new TransactionTemplate(manager);
    }

    public void requireReady() {
        // 필수 데이터 소유 모듈을 빼먹고 완료할 수 없도록 이름과 실제 준비 상태를 함께 검사한다.
        if (!domains.stream().map(AccountDeletionDomainCleanup::domain).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of("NOVEL", "VOICE"))
                || storage.getIfAvailable() == null || !storage.getIfAvailable().supportsAccountDeletion()) throw new IllegalStateException("Account deletion cleanup modules are not ready");
        jdbc.queryForObject("select count(*) from account_deletion_object", Long.class);
        jdbc.queryForObject("select count(*) from profile_object_write", Long.class);
        domains.forEach(AccountDeletionDomainCleanup::requireReady);
    }

    public void captureProfileObjects(String operationId, long userId) {
        jdbc.queryForList("select object_key from profile_object_write where user_id=?", String.class, userId)
                .forEach(key -> captureObject(operationId, userId, key));
        if (!table("user_profile")) return;
        for (String column : List.of("profile_image_object_key", "profile_background_image_object_key")) {
            jdbc.queryForList("select " + column + " from user_profile where user_id=? and " + column + " is not null", String.class, userId)
                    .forEach(key -> captureObject(operationId, userId, key));
        }
    }

    private void captureObject(String operationId, long userId, String key) {
        // DB의 잘못된 참조도 다른 계정/시스템 객체를 삭제할 권한으로 해석하지 않는다.
        if (!ProfileObjectWriteRegistry.ownedProfileKey(userId, key)) return;
        String hash = DeletionTokens.hash(key);
        if (jdbc.queryForObject("select count(*) from account_deletion_object where operation_id=? and object_hash=?", Long.class, operationId, hash) == 0) {
            jdbc.update("insert into account_deletion_object (operation_id,object_hash,object_key,completed) values (?,?,?,false)", operationId, hash, key);
        }
    }

    public void removeAuthenticationAndProfile(long userId, String email) {
        execute("refresh_token", "delete from refresh_token where user_id=?", userId);
        execute("auth_google_intent", "delete from auth_google_intent where user_id=?", userId);
        execute("auth_reauthentication_grant", "delete from auth_reauthentication_grant where user_id=?", userId);
        execute("auth_email_challenge", "delete from auth_email_challenge where user_id=? or email=?", userId, email);
        execute("auth_mobile_request", "delete from auth_mobile_request where approved_user_id=?", userId);
        execute("user_allowed", "delete from user_allowed where lower(email)=lower(?)", email);
        execute("user_profile", "delete from user_profile where user_id=?", userId);
        // 비동기 도메인 정리를 기다리는 동안에도 친구/요청/차단 목록에 계정 연결을 남기지 않는다.
        execute("friend_request", "delete from friend_request where requester_user_id=? or receiver_user_id=?", userId, userId);
        execute("friend", "delete from friend where user_low_id=? or user_high_id=?", userId, userId);
        execute("user_block", "delete from user_block where blocker_user_id=? or blocked_user_id=?", userId, userId);
    }

    public boolean clean(AccountDeletionStore.Operation operation) {
        // 한 도메인의 미확정 파일 때문에 나머지 도메인과 일반 개인정보 삭제를 계속 보류하지 않는다.
        boolean complete = true;
        RuntimeException firstFailure = null;
        for (AccountDeletionDomainCleanup domain : domains) {
            try {
                if (!domain.clean(operation)) complete = false;
            } catch (RuntimeException error) {
                complete = false;
                if (firstFailure == null) firstFailure = error;
            }
        }

        // 외부 객체 호출과 별개로 처리 가능한 개인 DB 정리를 커밋한다. 같은 작업 재시도도 멱등이다.
        tx.executeWithoutResult(status -> {
            // 완료 worker와 같은 operation 행을 잠근다. 오래된 lease의 결과는 이메일 재가입 후 감사 값을 건드리지 않는다.
            var current = jdbc.queryForMap("select status,audit_identity from account_deletion_operation where operation_id=? for update", operation.operationId());
            long user = operation.userId();
            execute("friend_request", "delete from friend_request where requester_user_id=? or receiver_user_id=?", user, user);
            execute("friend", "delete from friend where user_low_id=? or user_high_id=?", user, user);
            execute("user_block", "delete from user_block where blocker_user_id=? or blocked_user_id=?", user, user);
            execute("recent_view", "delete from recent_view where user_id=?", user);
            cleanSupport(user);
            cleanAccountBooks(user);
            if ("PENDING".equals(current.get("status"))) {
                // 모든 소유 서비스가 끝날 때까지 이메일 재가입이 예약되므로 접수 전 시작해 늦게 커밋한 감사 값도 제거한다.
                scrubAudit((String) current.get("audit_identity"), Instant.now());
            }
        });
        if (firstFailure != null) throw firstFailure;
        return complete;
    }

    public boolean cleanObjects(String operationId) {
        // BE_DATA가 객체 키를 추가할 수 있으므로 그 단계 완료 이전에는 객체 단계를 확정하지 않는다.
        if (jdbc.queryForObject("select count(*) from account_deletion_step where operation_id=? and step='BE_DATA' and status='COMPLETED'", Long.class, operationId) != 1) return false;
        ImageStoragePort objects = storage.getIfAvailable();
        if (objects == null) return false;
        var row = jdbc.queryForMap("select user_id,requested_at from account_deletion_operation where operation_id=?", operationId);
        long userId = ((Number) row.get("user_id")).longValue();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        for (String key : jdbc.queryForList("select object_key from account_deletion_object where operation_id=? and completed=false limit 100", String.class, operationId)) {
            if (System.nanoTime() >= deadline) return false;
            // 저장된 큐도 다시 확인한다. foreign prefix/경로 탈출은 실제 저장소에 전달하지 않는다.
            if (!ProfileObjectWriteRegistry.ownedProfileKey(userId, key)) return false;
            objects.delete(key);
            jdbc.update("update account_deletion_object set completed=true,object_key=null where operation_id=? and object_hash=?", operationId, DeletionTokens.hash(key));
        }
        if (jdbc.queryForObject("select count(*) from account_deletion_object where operation_id=? and completed=false", Long.class, operationId) != 0) return false;
        Instant requested = ((Timestamp) row.get("requested_at")).toInstant();
        // 접수 전 시작한 저장 요청의 최대 client deadline 이후 한 번 더 계정 prefix 전체를 확인한다.
        if (Instant.now().isBefore(requested.plusSeconds(45)) || System.nanoTime() >= deadline) return false;
        boolean empty = objects.deleteUserProfileObjects(userId);
        // 불명확한 외부 PUT은 삭제 후에도 늦게 완료될 수 있다. 객체가 지금 없다는 사실만으로 완료하지 않는다.
        long uncertain = jdbc.queryForObject("select count(*) from profile_object_write where user_id=? and state<>'ACKNOWLEDGED'", Long.class, userId);
        if (!empty || uncertain != 0) return false;
        jdbc.update("delete from profile_object_write where user_id=? and state='ACKNOWLEDGED'", userId);
        return true;
    }

    private void cleanSupport(long userId) {
        execute("support_notification", "delete from support_notification where recipient_id=?", userId);
        execute("support_notice_dismissal", "delete from support_notice_dismissal where user_id=?", userId);
        if (table("support_inquiry")) {
            for (Long id : jdbc.queryForList("select id from support_inquiry where user_id=?", Long.class, userId)) {
                execute("support_notification", "delete from support_notification where target_type='INQUIRY' and target_id=?", id);
                execute("support_reply", "update support_reply set parent_id=null where inquiry_id=?", id);
                execute("support_reply", "delete from support_reply where inquiry_id=?", id);
                jdbc.update("delete from support_inquiry where id=?", id);
            }
        }
        // 다른 문의에 남긴 답글과 공지는 tombstone 작성자를 참조하고, 개인 문의/쪽지는 제거한다.
        if (table("support_message")) {
            if (table("support_inquiry")) jdbc.update("update support_inquiry set related_message_id=null where related_message_id in (select id from support_message where sender_id=? or recipient_id=?)", userId, userId);
            jdbc.update("delete from support_message where sender_id=? or recipient_id=?", userId, userId);
        }
    }

    private void cleanAccountBooks(long userId) {
        if (!table("account_book")) return;
        // 공유 가계부는 다른 활성 계정 중 가장 오래된 멤버에게 넘기고 공동 거래 내역은 유지한다.
        List<Long> books = jdbc.queryForList("select id from account_book where user_id=? order by id for update", Long.class, userId);
        for (long book : books) {
            List<Long> remaining = table("account_book_member") ? jdbc.queryForList(
                    "select m.user_id from account_book_member m join `user` u on u.id=m.user_id where m.account_book_id=? and m.user_id<>? and m.deleted=false and u.account_state='ACTIVE' order by m.id limit 1", Long.class, book, userId) : List.of();
            if (!remaining.isEmpty()) {
                long successor = remaining.getFirst();
                jdbc.update("update account_book set user_id=? where id=?", successor, book);
                jdbc.update("update account_book_member set role='OWNER' where account_book_id=? and user_id=? and deleted=false", book, successor);
            } else {
                // 혼자 쓰던 가계부의 자식 레코드만 FK 순서로 제거한다. 전역 통화/영수증 규칙은 지우지 않는다.
                for (String child : List.of("receipt_batch_registration", "account_book_invitation", "account_book_member", "account_book_monthly_goals", "account_book_transactions", "account_book_fixed_cost", "account_book_category")) {
                    execute(child, "delete from " + child + " where account_book_id=?", book);
                }
                jdbc.update("delete from account_book where id=?", book);
            }
        }
        execute("account_book_member", "delete from account_book_member where user_id=?", userId);
        execute("account_book_invitation", "delete from account_book_invitation where inviter_user_id=? or invitee_user_id=?", userId, userId);
        execute("receipt_batch_registration", "delete from receipt_batch_registration where user_id=?", userId);
    }

    private void scrubAudit(String email, Instant requestedAt) {
        if (email == null) return;
        // 역사적 이메일 감사 값만 변경한다. 같은 이메일의 재가입 이후 생성/수정된 데이터는 건드리지 않는다.
        for (String table : tables()) {
            Set<String> columns = columns(table);
            for (String field : List.of("created_by", "updated_by")) {
                String time = field.equals("created_by") ? "created_at" : "updated_at";
                if (columns.contains(field) && columns.contains(time)) {
                    jdbc.update("update `" + table + "` set " + field + "='DELETED' where " + field + "=? and " + time + "<=?", email, Timestamp.from(requestedAt));
                }
            }
        }
    }

    private void execute(String table, String sql, Object... args) {
        if (table(table)) jdbc.update(sql, args);
    }

    private boolean table(String name) { return tables().contains(name); }

    private Set<String> tables() {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Set<String>>) connection -> {
            Set<String> result = new HashSet<>();
            try (var rows = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                while (rows.next()) {
                    String name = rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    if (name.matches("[a-z][a-z0-9_]*")) result.add(name);
                }
            }
            return result;
        });
    }

    private Set<String> columns(String table) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Set<String>>) connection -> {
            Set<String> result = new HashSet<>();
            try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), connection.getSchema(), "%", "%")) {
                while (rows.next()) if (table.equalsIgnoreCase(rows.getString("TABLE_NAME"))) result.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            return result;
        });
    }
}
