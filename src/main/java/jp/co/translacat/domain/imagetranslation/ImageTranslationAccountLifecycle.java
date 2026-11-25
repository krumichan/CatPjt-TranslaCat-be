package jp.co.translacat.domain.imagetranslation;

import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import jp.co.translacat.domain.user.deletion.AccountDeletionDomainCleanup;
import jp.co.translacat.domain.user.deletion.AccountDeletionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.Job;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.JobStatus;

/** アカウント削除に参加し、画像翻訳の実行・保存を閉じてから所有データを消去する。 */
@Component
@ConditionalOnProperty(prefix = "translacat.image-translation", name = "asset-secret")
final class ImageTranslationAccountLifecycle implements AccountDeletionDomainCleanup {
    private final ImageTranslationStore store;
    private final ImageTranslationAssets assets;
    private final AccountDataLifecycleAccess accounts;

    // アカウント側の公開ライフサイクル契約と、自分の機能が所有するデータだけを利用する。
    ImageTranslationAccountLifecycle(
            ImageTranslationStore store, ImageTranslationAssets assets, AccountDataLifecycleAccess accounts) {
        this.store = store;
        this.assets = assets;
        this.accounts = accounts;
    }

    /** {@inheritDoc} */
    @Override
    public String domain() {
        return "IMAGE_TRANSLATION";
    }

    /** {@inheritDoc} */
    @Override
    public void requireReady() {
        store.requireCleanupSchema();
    }

    /** {@inheritDoc} */
    @Override
    public boolean clean(AccountDeletionStore.Operation operation) {
        long owner = operation.userId();

        // 公開を先に止め、オブジェクト削除に必要な回の ID だけを残す。
        List<UUID> pending = store.transaction(() -> fenceOwnedData(owner));
        if (pending == null) {
            return false;
        }

        // オブジェクト I/O はトランザクション外で実行し、成功した回だけ記録を除去する。
        for (UUID jobId : pending) {
            try {
                assets.removeJob(jobId);
            } catch (IOException error) {
                return false;
            }
            if (!Boolean.TRUE.equals(store.transaction(() -> deletePurgedJob(owner, jobId)))) {
                return false;
            }
        }
        return store.ownedJobIds(owner).isEmpty();
    }

    // 書き込み側と同じアカウント→機能所有者→回の順にロックし、遅れた処理を無効にする。
    private List<UUID> fenceOwnedData(long owner) {
        if (!accounts.lockDeletionAccepted(owner)) {
            return null;
        }
        store.lockOwner(owner);
        store.markOwnerRemoved(owner);
        store.deleteOwnedRequests(owner);

        // 個人データは先に除去し、オブジェクトが消えるまで回 ID を再試行用に保持する。
        long now = Instant.now().toEpochMilli();
        List<UUID> jobs = store.ownedJobIds(owner);
        for (UUID jobId : jobs) {
            Job job = store.find(jobId, true);
            if (job != null) {
                if (job.owner != owner) {
                    throw new IllegalStateException("Image translation ownership index mismatch");
                }
                clearPersonalData(job, now);
                store.save(job, now);
            }
        }
        return jobs;
    }

    // 通常の期限処理に引き渡さず、オブジェクト削除が完了するまで回の ID を保持する。
    private static void clearPersonalData(Job job, long now) {
        job.status = JobStatus.EXPIRED;
        job.revision++;
        job.expiresAt = now;
        job.cleanupDue = Long.MAX_VALUE;
        job.tombstoneUntil = Long.MAX_VALUE;
        job.uploadExpiresAt = 0;
        job.prepareLease = null;
        job.prepareLeaseUntil = 0;
        job.prepareAttempts = 0;

        // 原本・翻訳・ファイル名などの内容は削除し、所有者と回 ID だけを対象特定に使う。
        job.sourceKind = null;
        job.sourceLanguage = null;
        job.targetLanguage = null;
        job.sourceLink = null;
        job.inputs = List.of();
        job.pages = List.of();
        job.inputIssues = List.of();
        job.failure = null;
        job.attribution = null;
    }

    // 物理削除済みの回を、所有者フェンスを確認した後に消去する。
    private boolean deletePurgedJob(long owner, UUID jobId) {
        if (!accounts.lockDeletionAccepted(owner) || !store.lockOwner(owner)) {
            return false;
        }
        Job job = store.find(jobId, true);
        if (job == null) {
            return true;
        }
        if (job.owner != owner || job.status != JobStatus.EXPIRED || job.cleanupDue != Long.MAX_VALUE) {
            return false;
        }
        store.delete(jobId);
        return true;
    }
}
