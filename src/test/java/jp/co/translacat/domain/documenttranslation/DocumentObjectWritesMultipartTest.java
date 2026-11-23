package jp.co.translacat.domain.documenttranslation;

import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentObjectWritesMultipartTest {
    private static final long OWNER = 98;
    private static final UUID JOB = UUID.fromString("d71de002-4216-4adb-92e4-fe6915840c9d");
    private static final String KEY = "source-" + JOB;
    private static final String UPLOAD = "fixture-upload-id";
    private static final Path SOURCE = Path.of("fixture.docx");
    private static final String ATTACH = "SET state='WRITING', upload_id=?";
    private static final String SETTLE = "SET state='SETTLED'";
    private static final String DROP_PREPARATION = "state='PREPARING' AND upload_id IS NULL";
    private static final String DROP_RECONCILED = "upload_id=? AND state='RECONCILING'";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final AccountDataLifecycleAccess accounts = mock(AccountDataLifecycleAccess.class);
    private final DocumentFiles.PrivateBucket bucket = mock(DocumentFiles.PrivateBucket.class);
    private final DocumentObjectWrites writes = new DocumentObjectWrites(jdbc, manager, accounts);

    // 각 저장소 호출 전에 어떤 트랜잭션이 커밋됐는지 검사할 수 있도록 경계를 제공한다.
    @BeforeEach
    void transactions() {
        when(manager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        when(accounts.lockActive(OWNER)).thenReturn(true);
    }

    // PREPARING 커밋 뒤 세션을 만들고, ID가 커밋된 뒤에만 문서 바이트를 전송한다.
    @Test
    void commitsPreparationAndUploadIdBeforeTakingEachStorageStep() throws Exception {
        when(bucket.createMultipart(KEY)).thenReturn(UPLOAD);
        permitAttachment();
        when(jdbc.update(contains(SETTLE), eq(OWNER), eq(JOB.toString()), eq(KEY), eq(UPLOAD))).thenReturn(1);

        assertTrue(writes.store(bucket, OWNER, JOB, KEY, SOURCE, () -> true));

        InOrder ordering = inOrder(accounts, jdbc, manager, bucket);
        ordering.verify(accounts).lockActive(OWNER);
        ordering.verify(jdbc).update(contains("INSERT INTO document_translation_object_write"),
                eq(KEY), eq(OWNER), eq(JOB.toString()), eq("PREPARING"));
        ordering.verify(manager).commit(any(TransactionStatus.class));
        ordering.verify(bucket).createMultipart(KEY);
        ordering.verify(accounts).lockActive(OWNER);
        ordering.verify(jdbc).update(contains(ATTACH), eq(UPLOAD), eq(OWNER), eq(JOB.toString()), eq(KEY));
        ordering.verify(manager).commit(any(TransactionStatus.class));
        ordering.verify(bucket).writeMultipart(KEY, UPLOAD, SOURCE);
        ordering.verify(jdbc).update(contains(SETTLE), eq(OWNER), eq(JOB.toString()), eq(KEY), eq(UPLOAD));
        ordering.verify(manager).commit(any(TransactionStatus.class));
    }

    // 세션 생성 중 탈퇴가 접수되면 두 번째 계정 확인으로 바이트 전송을 거부한다.
    @Test
    void secondAccountCheckClosesAnEmptySessionWithoutWriting() throws Exception {
        when(bucket.createMultipart(KEY)).thenReturn(UPLOAD);
        when(accounts.lockActive(OWNER)).thenReturn(true, false);

        assertFalse(writes.store(bucket, OWNER, JOB, KEY, SOURCE, () -> true));

        verify(bucket).abortMultipart(KEY, UPLOAD);
        verify(bucket, never()).writeMultipart(any(), any(), any());
        verify(jdbc, never()).update(contains(ATTACH), eq(UPLOAD), eq(OWNER), eq(JOB.toString()), eq(KEY));
        verify(jdbc).update(contains(DROP_PREPARATION), eq(OWNER), eq(JOB.toString()), eq(KEY));
    }

    // 늦게 도착한 생성 응답은 정리된 PREPARING을 되살리거나 저장을 시작할 수 없다.
    @Test
    void lateCreateCannotReattachAfterPreparationWasDeleted() throws Exception {
        AtomicBoolean preparationExists = new AtomicBoolean(true);
        stubState("PREPARING", null);
        when(jdbc.update(contains(DROP_PREPARATION), eq(OWNER), eq(JOB.toString()), eq(KEY)))
                .thenAnswer(ignored -> preparationExists.getAndSet(false) ? 1 : 0);
        when(jdbc.update(contains(ATTACH), eq(UPLOAD), eq(OWNER), eq(JOB.toString()), eq(KEY)))
                .thenAnswer(ignored -> preparationExists.get() ? 1 : 0);
        when(bucket.createMultipart(KEY)).thenAnswer(ignored -> {
            writes.reconcile(bucket, OWNER, JOB, KEY);
            return UPLOAD;
        });

        assertFalse(writes.store(bucket, OWNER, JOB, KEY, SOURCE, () -> true));

        InOrder ordering = inOrder(jdbc, bucket);
        ordering.verify(jdbc).update(contains(DROP_PREPARATION), eq(OWNER), eq(JOB.toString()), eq(KEY));
        ordering.verify(jdbc).update(contains(ATTACH), eq(UPLOAD), eq(OWNER), eq(JOB.toString()), eq(KEY));
        ordering.verify(bucket).abortMultipart(KEY, UPLOAD);
        verify(bucket, never()).writeMultipart(any(), any(), any());
    }

    // 완료 응답과 첫 정리 증거를 잃어도 동일한 ID를 보존하고, 확인된 두 번째 시도만 원장을 제거한다.
    @Test
    void ambiguousCompletionKeepsTheIdUntilConfirmedReconciliation() throws Exception {
        when(bucket.createMultipart(KEY)).thenReturn(UPLOAD);
        permitAttachment();
        doThrow(new DocumentFiles.UnconfirmedWrite()).when(bucket).writeMultipart(KEY, UPLOAD, SOURCE);
        assertThrows(DocumentFiles.UnconfirmedWrite.class, () -> writes.store(bucket, OWNER, JOB, KEY, SOURCE,
                () -> true));
        verify(jdbc, never()).update(contains(SETTLE), eq(OWNER), eq(JOB.toString()), eq(KEY), eq(UPLOAD));

        stubState("WRITING", UPLOAD);
        when(jdbc.update(contains("SET state='RECONCILING'"), eq(OWNER), eq(JOB.toString()), eq(KEY), eq(UPLOAD)))
                .thenReturn(1);
        doThrow(new DocumentFiles.UnconfirmedWrite()).doNothing().when(bucket).reconcileMultipart(KEY, UPLOAD);
        assertThrows(DocumentFiles.UnconfirmedWrite.class, () -> writes.reconcile(bucket, OWNER, JOB, KEY));
        verify(jdbc, never()).update(contains(DROP_RECONCILED), eq(OWNER), eq(JOB.toString()), eq(KEY), eq(UPLOAD));

        stubState("RECONCILING", UPLOAD);
        writes.reconcile(bucket, OWNER, JOB, KEY);

        verify(bucket, times(2)).reconcileMultipart(KEY, UPLOAD);
        verify(bucket, never()).delete(any());
        verify(jdbc).update(contains(DROP_RECONCILED), eq(OWNER), eq(JOB.toString()), eq(KEY), eq(UPLOAD));
    }

    // 저장 호출 직전 PREPARING에서 WRITING으로 전이하는 조건부 UPDATE만 성공시킨다.
    private void permitAttachment() {
        when(jdbc.update(contains(ATTACH), eq(UPLOAD), eq(OWNER), eq(JOB.toString()), eq(KEY))).thenReturn(1);
    }

    // 현재 원장 조회에 실제 RowMapper를 적용한다.
    private void stubState(String state, String uploadId) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn(state);
        when(row.getString(2)).thenReturn(uploadId);
        when(jdbc.query(contains("SELECT state,upload_id"), ArgumentMatchers.<RowMapper<Object>>any(),
                eq(OWNER), eq(JOB.toString()), eq(KEY))).thenAnswer(queried -> {
                    RowMapper<Object> mapper = queried.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
    }
}
