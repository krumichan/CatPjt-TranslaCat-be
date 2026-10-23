package jp.co.translacat.domain.user.deletion;

import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountDeletionPersonalDataTest {
    private JdbcTemplate jdbc;
    private AccountDeletionPersonalData cleanup;
    private ImageStoragePort storage;
    private AccountDeletionDomainCleanup novel;
    private AccountDeletionDomainCleanup voice;
    private final String operation = UUID.randomUUID().toString();
    // 서버 현지 날짜와 UTC의 차이에 의존하지 않고 grace/cutoff보다 확실히 오래된 접수를 만든다.
    private final Instant requested = Instant.now().minusSeconds(120);

    @BeforeEach
    void prepare() {
        // 준비: 기존 사용자/공유 가계부/개인 객체를 표현하는 격리 DB이며 외부 저장소는 fake다.
        var source = new DriverManagerDataSource("jdbc:h2:mem:deletion-personal-" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table `user`(id bigint primary key,account_state varchar(24))");
        jdbc.execute("create table account_book(id bigint primary key,user_id bigint references `user`(id))");
        jdbc.execute("create table account_book_member(id bigint primary key,account_book_id bigint references account_book(id),user_id bigint references `user`(id),deleted boolean,role varchar(24))");
        jdbc.execute("create table account_book_transactions(id bigint primary key,account_book_id bigint references account_book(id),memo varchar(100))");
        jdbc.execute("create table account_book_monthly_goals(id bigint primary key,account_book_id bigint references account_book(id))");
        jdbc.execute("create table account_deletion_object(operation_id varchar(36),object_hash varchar(64),object_key varchar(500),completed boolean,primary key(operation_id,object_hash))");
        jdbc.execute("create table account_deletion_step(operation_id varchar(36),step varchar(24),status varchar(24))");
        jdbc.execute("create table account_deletion_operation(operation_id varchar(36),user_id bigint,requested_at timestamp,status varchar(24),audit_identity varchar(320))");
        jdbc.update("insert into account_deletion_operation values(?,1,?,'PENDING','old@example.com')", operation, Timestamp.from(requested));
        jdbc.execute("create table user_profile(id bigint,user_id bigint,profile_image_object_key varchar(500),profile_background_image_object_key varchar(500))");
        jdbc.execute("create table user_allowed(id bigint,email varchar(320))");
        jdbc.execute("create table profile_object_write(id varchar(36),user_id bigint,object_key varchar(500),state varchar(24))");
        jdbc.execute("create table audit_probe(id bigint,created_by varchar(50),created_at timestamp,updated_by varchar(50),updated_at timestamp)");
        jdbc.update("insert into `user` values(1,'DELETION_PENDING'),(2,'ACTIVE'),(3,'ACTIVE')");
        storage = mock(ImageStoragePort.class);
        when(storage.supportsAccountDeletion()).thenReturn(true);
        when(storage.deleteUserProfileObjects(1)).thenReturn(true);
        novel = domain("NOVEL");
        voice = domain("VOICE");
        var beans = new StaticListableBeanFactory();
        beans.addBean("storage", storage);
        cleanup = new AccountDeletionPersonalData(jdbc, beans.getBeanProvider(ImageStoragePort.class), List.of(novel, voice), new DataSourceTransactionManager(source));
    }

    @Test
    void sharedBookIsTransferredAndPersonalBookIsPhysicallyDeletedWithoutAffectingAnotherOwnersBook() {
        // 준비
        jdbc.update("insert into account_book values(10,1),(20,1),(30,2)");
        jdbc.update("insert into account_book_member values(1,10,1,false,'OWNER'),(2,10,2,false,'MEMBER'),(3,10,3,false,'MEMBER'),(4,20,1,false,'OWNER'),(5,30,1,false,'MEMBER'),(6,30,2,false,'OWNER')");
        jdbc.update("insert into account_book_transactions values(100,10,'shared'),(200,20,'private'),(300,30,'other')");
        jdbc.update("insert into account_book_monthly_goals values(1,20)");

        // 실행
        assertThat(cleanup.clean(context())).isTrue();

        // 검증: 상대 회원에게 소유권을 주고 공유 거래의 본문은 바꾸지 않는다.
        assertThat(jdbc.queryForObject("select user_id from account_book where id=10", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select role from account_book_member where account_book_id=10 and user_id=2", String.class)).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("select count(*) from account_book where id=20", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select id from account_book_transactions order by id", Long.class)).containsExactly(100L, 300L);
        assertThat(jdbc.queryForObject("select memo from account_book_transactions where id=100", String.class)).isEqualTo("shared");
        assertThat(jdbc.queryForObject("select count(*) from account_book_member where user_id=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select user_id from account_book where id=30", Long.class)).isEqualTo(2);
    }

    @Test
    void incompleteDomainStillDeletesIndependentPersonalDataWithoutCompletingTheAggregate() {
        // 준비
        jdbc.update("insert into account_book values(20,1)");
        when(novel.clean(any())).thenReturn(false);

        // 실행
        assertThat(cleanup.clean(context())).isFalse();

        // 검증
        assertThat(jdbc.queryForObject("select count(*) from account_book", Integer.class)).isZero();
        verify(voice).clean(any());
    }

    @Test
    void failedDomainStillDeletesIndependentPersonalDataAndRemainsRetryable() {
        // 준비
        jdbc.update("insert into account_book values(20,1)");
        when(novel.clean(any())).thenThrow(new IllegalStateException("synthetic storage outage"));

        // 실행
        assertThatThrownBy(() -> cleanup.clean(context())).isInstanceOf(IllegalStateException.class);

        // 검증: 외부 도메인 오류가 있어도 개인 DB 정리는 커밋하고 완료 처리는 하지 않는다.
        assertThat(jdbc.queryForObject("select count(*) from account_book", Integer.class)).isZero();
        verify(voice).clean(any());
    }

    @Test
    void pendingReservationAllowsScrubbingPreacceptanceWritesThatCommittedLate() {
        // 준비
        jdbc.update("insert into audit_probe values(1,'old@example.com',?,'old@example.com',?)", Timestamp.from(requested.minusSeconds(1)), Timestamp.from(requested.plusSeconds(1)));

        // 실행
        cleanup.clean(context());

        // 검증: PENDING 중에는 동일 이메일 재가입이 금지되어 접수 후 늦게 커밋된 옛 감사 값까지 지운다.
        assertThat(jdbc.queryForObject("select created_by from audit_probe where id=1", String.class)).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select updated_by from audit_probe where id=1", String.class)).isEqualTo("DELETED");
    }

    @Test
    void staleCleanupAfterCompletionNeverScrubsANewAccountsAuditWithTheSameEmail() {
        // 준비: 완료가 예약을 해제한 다음 같은 이메일로 만들어진 기록이다.
        jdbc.update("update account_deletion_operation set status='COMPLETED',audit_identity=null where operation_id=?", operation);
        jdbc.update("insert into audit_probe values(1,'old@example.com',?,'old@example.com',?)", Timestamp.from(requested.plusSeconds(10)), Timestamp.from(requested.plusSeconds(10)));

        // 실행: 임대가 만료된 worker가 이전 operation 사본으로 뒤늦게 돌아온다.
        cleanup.clean(context());

        // 검증
        assertThat(jdbc.queryForObject("select created_by from audit_probe where id=1", String.class)).isEqualTo("old@example.com");
        assertThat(jdbc.queryForObject("select updated_by from audit_probe where id=1", String.class)).isEqualTo("old@example.com");
    }

    @Test
    void profileObjectDeletionFailureRemainsDurableAndSameObjectRetrySucceeds() {
        // 준비
        jdbc.update("insert into user_profile values(1,1,'user-profile/1/profile/private.jpg','user-profile/1/background/background.jpg')");
        jdbc.update("insert into user_allowed values(1,'Old@Example.com'),(2,'another@example.com')");
        cleanup.captureProfileObjects(operation, 1);
        cleanup.removeAuthenticationAndProfile(1, "old@example.com");
        assertThat(jdbc.queryForObject("select count(*) from user_profile", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select email from user_allowed", String.class)).containsExactly("another@example.com");
        assertThat(cleanup.cleanObjects(operation)).isFalse();
        jdbc.update("insert into account_deletion_step values(?,'BE_DATA','COMPLETED')", operation);
        doThrow(new IllegalStateException("synthetic storage outage")).when(storage).delete(anyString());

        // 실행
        assertThatThrownBy(() -> cleanup.cleanObjects(operation)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_object where completed=false", Integer.class)).isEqualTo(2);
        doNothing().when(storage).delete(anyString());
        assertThat(cleanup.cleanObjects(operation)).isTrue();

        // 검증: 실제 객체 삭제 성공 뒤에만 큐에서 원문 key를 제거한다.
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_object where object_key is not null", Integer.class)).isZero();
        verify(storage, atLeastOnce()).delete("user-profile/1/background/background.jpg");
        verify(storage, atLeastOnce()).delete("user-profile/1/profile/private.jpg");
    }

    @Test
    void foreignAndTraversalKeysAreNeverPassedToObjectDeletion() {
        // 준비: 참조 데이터가 잘못되어도 다른 계정과 시스템 파일을 삭제하지 않는다.
        jdbc.update("insert into user_profile values(1,1,'user-profile/10/profile/other.jpg','user-profile/1/profile/../../shared.jpg')");
        cleanup.captureProfileObjects(operation, 1);
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_object", Integer.class)).isZero();
        String foreignKey = "user-profile/10/profile/other.jpg";
        jdbc.update("insert into account_deletion_object values(?,?,?,false)", operation, DeletionTokens.hash(foreignKey), foreignKey);
        jdbc.update("insert into account_deletion_step values(?,'BE_DATA','COMPLETED')", operation);

        // 실행
        assertThat(cleanup.cleanObjects(operation)).isFalse();

        // 검증: 큐가 손상되었어도 저장소 호출 없이 미완료 상태를 유지한다.
        verify(storage, never()).delete(anyString());
    }

    private AccountDeletionDomainCleanup domain(String name) {
        var domain = mock(AccountDeletionDomainCleanup.class);
        when(domain.domain()).thenReturn(name);
        when(domain.clean(any())).thenReturn(true);
        return domain;
    }

    @Test
    void uncertainOrInterruptedProfilePutNeverCompletesFromEmptyStorageOrElapsedTimeAlone() {
        // 준비: 제공자의 응답을 잃은 PUT은 며칠이 지나도 terminal 증거가 아니다.
        jdbc.update("insert into profile_object_write values('uncertain',1,'user-profile/1/profile/uncertain.jpg','UNCERTAIN')");
        jdbc.update("insert into profile_object_write values('interrupted',1,'user-profile/1/profile/interrupted.jpg','STARTED')");
        jdbc.update("insert into account_deletion_step values(?,'BE_DATA','COMPLETED')", operation);
        cleanup.captureProfileObjects(operation, 1);

        // 실행: 현재 빈 저장소라는 응답도 미확정 PUT의 미래 완료 가능성을 없애지 않는다.
        assertThat(cleanup.cleanObjects(operation)).isFalse();
        assertThat(cleanup.cleanObjects(operation)).isFalse();

        // 검증
        assertThat(jdbc.queryForObject("select count(*) from profile_object_write where state<>'ACKNOWLEDGED'", Integer.class)).isEqualTo(2);
        verify(storage, times(2)).deleteUserProfileObjects(1);
    }

    private AccountDeletionStore.Operation context() {
        return new AccountDeletionStore.Operation(operation, 1, "hash", "PENDING", requested, null, null, "old@example.com");
    }
}
