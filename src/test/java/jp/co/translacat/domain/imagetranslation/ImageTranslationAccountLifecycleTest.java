package jp.co.translacat.domain.imagetranslation;

import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import jp.co.translacat.domain.user.deletion.AccountDeletionStore.Operation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.FileRequest;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.Input;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.InputIssue;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.Job;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.JobStatus;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.Page;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.SourceKind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageTranslationAccountLifecycleTest {
    private static final long OWNER = 41;

    // オブジェクト削除の失敗で実行が復活せず、同じ削除操作を再試行できることを確認する。
    @Test
    void scrubsAndFencesBeforePurgeAndRetriesFailedAssetRemoval() throws IOException {
        // 準備: 原本情報と実行中のリースを持つ一件を用意し、初回の物理削除を失敗させる。
        ImageTranslationStore store = transactionalStore();
        var assets = mock(ImageTranslationAssets.class);
        var accounts = mock(AccountDataLifecycleAccess.class);
        when(accounts.lockDeletionAccepted(OWNER)).thenReturn(true);
        when(store.lockOwner(OWNER)).thenReturn(false, true, true);
        Job running = personalJob();
        when(store.ownedJobIds(OWNER)).thenReturn(List.of(running.id), List.of(running.id), List.of());
        when(store.find(running.id, true)).thenReturn(running);
        doThrow(new IOException("fixture failure")).doNothing().when(assets).removeJob(running.id);
        var lifecycle = new ImageTranslationAccountLifecycle(store, assets, accounts);

        // 実行: オブジェクト削除が成功するまで同じアカウント削除を再試行する。
        boolean first = lifecycle.clean(operation());

        // 検証: 初回の失敗でも残るのは再試行に必要な回 ID だけである。
        assertFalse(first);
        assertEquals(JobStatus.EXPIRED, running.status);
        assertEquals(Long.MAX_VALUE, running.cleanupDue);
        assertEquals(Long.MAX_VALUE, running.tombstoneUntil);
        assertNull(running.sourceLink);
        assertNull(running.sourceLanguage);
        assertNull(running.prepareLease);
        assertTrue(running.pages.isEmpty());
        assertTrue(running.inputs.isEmpty());
        assertTrue(running.inputIssues.isEmpty());
        verify(store, never()).delete(running.id);
        var order = inOrder(accounts, store, assets);
        order.verify(accounts).lockDeletionAccepted(OWNER);
        order.verify(store).lockOwner(OWNER);
        order.verify(store).markOwnerRemoved(OWNER);
        order.verify(store).deleteOwnedRequests(OWNER);
        order.verify(store).save(eq(running), anyLong());
        order.verify(assets).removeJob(running.id);

        // 再試行で物理削除が成功し、同じ所有者フェンスの下で記録を削除する。
        assertTrue(lifecycle.clean(operation()));
        verify(store).delete(running.id);
    }

    // 過去に回を作っていないアカウントにも、遅れた呼び出しを拒否するフェンスを残す。
    @Test
    void fencesDeletionEvenWhenThereAreNoOwnedJobs() {
        // 準備: 削除が受理されたオブジェクトのない所有者を用意する。
        ImageTranslationStore store = transactionalStore();
        var assets = mock(ImageTranslationAssets.class);
        var accounts = mock(AccountDataLifecycleAccess.class);
        when(accounts.lockDeletionAccepted(OWNER)).thenReturn(true);
        when(store.ownedJobIds(OWNER)).thenReturn(List.of());
        var lifecycle = new ImageTranslationAccountLifecycle(store, assets, accounts);

        // 実行: 空のデータ領域を削除する。
        boolean done = lifecycle.clean(operation());

        // 検証: アカウントのフェンスは直ちに公開される。
        assertTrue(done);
        verify(store).markOwnerRemoved(OWNER);
        verify(store).deleteOwnedRequests(OWNER);
        verifyNoInteractions(assets);
    }

    // アカウント削除が受理されていないときは、所有者の回に触れない。
    @Test
    void requiresAcceptedDeletionBeforeTouchingOwnedData() {
        // 準備: 削除の受付を確認できないアカウント状態を用意する。
        ImageTranslationStore store = transactionalStore();
        var assets = mock(ImageTranslationAssets.class);
        var accounts = mock(AccountDataLifecycleAccess.class);
        var lifecycle = new ImageTranslationAccountLifecycle(store, assets, accounts);

        // 実行: この状態で削除のコールバックが実行される。
        boolean done = lifecycle.clean(operation());

        // 検証: 所有データは変更されず、完了も報告しない。
        assertFalse(done);
        verify(store, never()).lockOwner(OWNER);
        verify(store, never()).ownedJobIds(OWNER);
        verifyNoInteractions(assets);
    }

    // 索引で取得した ID と保存済み所有者が異なる場合、物理削除に進まない。
    @Test
    void doesNotPurgeAnInconsistentOwnedRow() {
        // 準備: 所有者索引と実体が食い違っているレスポンスを作る。
        ImageTranslationStore store = transactionalStore();
        var assets = mock(ImageTranslationAssets.class);
        var accounts = mock(AccountDataLifecycleAccess.class);
        Job foreign = personalJob();
        foreign.owner = 42;
        when(accounts.lockDeletionAccepted(OWNER)).thenReturn(true);
        when(store.ownedJobIds(OWNER)).thenReturn(List.of(foreign.id));
        when(store.find(foreign.id, true)).thenReturn(foreign);
        var lifecycle = new ImageTranslationAccountLifecycle(store, assets, accounts);

        // 実行と検証: 照合が失敗し、他の回のファイルは残る。
        assertThrows(IllegalStateException.class, () -> lifecycle.clean(operation()));
        verifyNoInteractions(assets);
    }

    // 本番のトランザクション内処理を順序どおり通すテスト用ストアを作る。
    private static ImageTranslationStore transactionalStore() {
        var store = mock(ImageTranslationStore.class);
        when(store.transaction(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        return store;
    }

    // 個人データと実行中の準備情報を保持する状態を再現する。
    private static Job personalJob() {
        var job = new Job();
        job.id = UUID.randomUUID();
        job.owner = OWNER;
        job.status = JobStatus.PREPARING;
        job.sourceKind = SourceKind.FILES;
        job.sourceLanguage = "ja";
        job.targetLanguage = "en";
        job.sourceLink = "https://fixture.invalid/chapter";
        job.prepareLease = UUID.randomUUID();
        job.inputs = List.of(new Input(UUID.randomUUID(), new FileRequest("fixture", "page.png", "image/png", 160)));
        job.pages = List.of(new Page());
        job.inputIssues = List.of(new InputIssue("missing.png", "INVALID_IMAGE", null));
        return job;
    }

    // アカウント削除ワーカーがドメインに渡す公開操作情報だけを組み立てる。
    private static Operation operation() {
        return new Operation("fixture-operation", OWNER, null, "PENDING", Instant.EPOCH, null, null, null);
    }
}
