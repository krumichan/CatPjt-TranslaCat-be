package jp.co.translacat.domain.imagetranslation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import software.amazon.awssdk.services.s3.S3Client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.CreateRequest;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.EngineResult;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.Failure;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.FileRequest;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.ImageInfo;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.JobStatus;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.PageState;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.RequestFailure;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.SourceKind;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.SourceRequest;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.Stage;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.WorkFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageTranslationContractsTest {
    private static final String SUPPORTED_EPISODE =
            "https://mimiandeunice.com/2011/06/07/censorship-vs-copyright/";
    private static final String AI_ENDPOINT = "http://localhost:18421/internal/v1/image-translation/page";
    private static final Map<String, String> AI_HEADERS = Map.of("X-API-KEY", "fixture-key");

    // JPEG の EXIF 方向と PNG の通常の寸法を同じ表示ピクセル空間で確認する。
    @Test
    void inspectsJpegExifAndPngDimensions() throws IOException {
        // 準備: raw JPEG と PNG を同じ幅・高さで用意し、JPEG にだけ EXIF 回転を設定する。
        byte[] jpeg = withClockwiseQuarterTurn(image("jpg", 120, 90));
        byte[] png = image("png", 120, 90);

        // 実行: 保存前の入力検査を両形式に適用する。
        ImageInfo fromJpeg = ImageTranslationInput.inspect(jpeg, "image/jpeg");
        ImageInfo fromPng = ImageTranslationInput.inspect(png, "image/png");

        // 検証: JPEG の表示寸法だけが入れ替わる。
        assertEquals(new ImageInfo("image/jpeg", 90, 120), fromJpeg);
        assertEquals(new ImageInfo("image/png", 120, 90), fromPng);
    }

    // 宣言 MIME と実体の相違、および表示幅上限を別々に拒否する。
    @Test
    void rejectsMismatchedTypeAndOversizeDimensions() throws IOException {
        // 準備: 小さい PNG と横幅だけが上限を超える PNG を用意する。
        byte[] png = image("png", 120, 90);
        byte[] wide = image("png", 4_097, 2);

        // 実行と検証: それぞれが固有の再試行不能エラーになる。
        WorkFailure mismatch = assertThrows(WorkFailure.class,
                () -> ImageTranslationInput.inspect(png, "image/jpeg"));
        WorkFailure oversized = assertThrows(WorkFailure.class,
                () -> ImageTranslationInput.inspect(wide, "image/png"));
        assertEquals(new Failure("UNSUPPORTED_MEDIA_TYPE", Stage.VALIDATE, false), mismatch.failure);
        assertEquals(new Failure("INPUT_LIMIT_EXCEEDED", Stage.VALIDATE, false), oversized.failure);
    }

    // リンク入力は設定と固定の対応回が一致した場合だけ準備可能と判定する。
    @Test
    void allowsOnlyAnExplicitlyEnabledCanonicalEpisodeWithoutNetwork() {
        // 準備: 空の許可設定と固定回を含む設定を用意する。
        var disabled = new ImageTranslationLinks("");
        var enabled = new ImageTranslationLinks(SUPPORTED_EPISODE + ", https://example.invalid/episode/");

        // 実行と検証: 正規 URL の判定はネットワークを使わずに完了する。
        assertEquals(SUPPORTED_EPISODE, enabled.canonical(SUPPORTED_EPISODE));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, assertThrows(
                RequestFailure.class, () -> disabled.canonical(SUPPORTED_EPISODE)).status);
        assertEquals("UNSUPPORTED_LINK", assertThrows(
                RequestFailure.class, () -> enabled.canonical(SUPPORTED_EPISODE + "?alternate=1")).code);
        assertEquals("UNSUPPORTED_LINK", assertThrows(
                RequestFailure.class, () -> enabled.canonical("https://example.invalid/episode/")).code);
    }

    // 内部 API の有効な OCR v1 応答を、元画像に対応する一度の呼び出しで受け取る。
    @Test
    void keepsNormalizedPolygonAndNullableConfidence() throws JsonProcessingException {
        // 準備: 一つの領域を持つ正の応答と内部サービス設定を用意する。
        var client = mock(ExternalApiClient.class);
        when(client.postMultipartOnce(eq(AI_ENDPOINT), any(), eq(AI_HEADERS), eq(JsonNode.class)))
                .thenReturn(pageResponse(65, 0, 90));
        var engine = new ImageTranslationEngine(client, "http://localhost:18421/", "fixture-key");

        // 実行: 検査済み JPEG に対して内部 OCR・翻訳を呼ぶ。
        EngineResult result = engine.process(new byte[0], new ImageInfo("image/jpeg", 90, 120), "ja", "en");

        // 検証: 順序、座標、翻訳、明示的な null が保存可能な応答に残る。
        assertEquals(PageState.READY, result.state());
        assertEquals(1, result.ocrContractVersion());
        assertEquals(List.of(10.0, 20.0), result.segments().getFirst().polygon().getFirst());
        assertEquals("Hello.", result.segments().getFirst().translatedText());
        assertNull(result.segments().getFirst().confidence());
        assertFalse(result.retryable());
        verify(client).postMultipartOnce(eq(AI_ENDPOINT), any(), eq(AI_HEADERS), eq(JsonNode.class));
    }

    // 原本寸法、連続順序、描画座標の契約に違反する応答を保存しない。
    @Test
    void rejectsMalformedAiGeometryOrderAndImageDimensions() throws JsonProcessingException {
        // 準備: 項目を一つずつ壊した三つの応答を用意する。
        var client = mock(ExternalApiClient.class);
        when(client.postMultipartOnce(eq(AI_ENDPOINT), any(), eq(AI_HEADERS), eq(JsonNode.class)))
                .thenReturn(pageResponse(91, 0, 90), pageResponse(65, 1, 90), pageResponse(65, 0, 120));
        var engine = new ImageTranslationEngine(client, "http://localhost:18421", "fixture-key");
        var image = new ImageInfo("image/jpeg", 90, 120);

        // 実行と検証: 何度再取得しても解決しない契約違反として失敗する。
        for (int index = 0; index < 3; index++) {
            WorkFailure error = assertThrows(WorkFailure.class,
                    () -> engine.process(new byte[0], image, "ja", "en"));
            assertEquals(new Failure("DISPLAY_DATA_INVALID", Stage.RENDER, false), error.failure);
        }
    }

    // 実際の移行 SQL と JDBC ストアで、受理済みキー・所有者・期限の境界を確認する。
    @Test
    void usesCurrentOwnedSnapshotForReplayAndHidesExpiredJobFromOtherOwners() throws SQLException {
        // 準備: テスト固有の H2 を MySQL 互換モードで起動し、製品の移行 SQL を適用する。
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:image_translation_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new FileSystemResource("scripts/sql/20261122_image_translation.sql"))
                .execute(dataSource);
        var mapper = new ObjectMapper();
        var store = new ImageTranslationStore(
                jdbc, mapper, new DataSourceTransactionManager(dataSource), activeAccount());
        var jobs = new ImageTranslationJobs(
                store, mock(ImageTranslationAssets.class), new ImageTranslationLinks(SUPPORTED_EPISODE), mapper);
        var source = new SourceRequest(SourceKind.LINK, null, null, SUPPORTED_EPISODE);
        var request = new CreateRequest("ja", "en", source);
        String key = "fixture-replay";

        try {
            // 実行と検証: 一度だけ受理し、同じキーの異なる本文と他人による閲覧を拒否する。
            var received = jobs.create(41, key, request);
            UUID id = received.jobId();
            assertEquals(JobStatus.PREPARING, received.status());
            assertEquals(id, jobs.create(41, key, request).jobId());
            assertEquals(HttpStatus.CONFLICT, assertThrows(RequestFailure.class,
                    () -> jobs.create(41, key, new CreateRequest("ja", "ko", source))).status);
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(RequestFailure.class, () -> jobs.get(42, id)).status);

            // 取り消し後の再送信は古い結果を返さず、状態と revision を維持する。
            var cancelled = jobs.cancel(41, id, key);
            assertEquals(JobStatus.CANCELLED, cancelled.status());
            assertEquals(cancelled.revision(), jobs.cancel(41, id, key).revision());
            assertEquals(JobStatus.CANCELLED, jobs.create(41, key, request).status());

            // 時刻を進める代わりに期限を過去に固定し、所有者と他人の応答を比較する。
            store.transaction(() -> {
                var job = store.find(id, true);
                job.expiresAt = 1;
                store.save(job, 1);
                return null;
            });
            assertEquals(List.of(id), store.expiredIds(1));
            assertEquals(HttpStatus.GONE, assertThrows(RequestFailure.class, () -> jobs.get(41, id)).status);
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(RequestFailure.class, () -> jobs.get(42, id)).status);
        } finally {
            shutdownDatabase(dataSource);
        }
    }

    // 異なる冪等キーによる初回の並行作成でも所有者の上限を越えない。
    @Test
    void serializesDifferentKeysAgainstTheOwnerQuota() throws Exception {
        // 準備: 本物の移行 SQL と同時トランザクションを使い、外部のアカウントロックは置き換える。
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:image_quota_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new FileSystemResource("scripts/sql/20261122_image_translation.sql"))
                .execute(dataSource);
        var mapper = new ObjectMapper();
        var store = new ImageTranslationStore(
                jdbc, mapper, new DataSourceTransactionManager(dataSource), activeAccount());
        var jobs = new ImageTranslationJobs(
                store, mock(ImageTranslationAssets.class), new ImageTranslationLinks(SUPPORTED_EPISODE), mapper);
        var request = new CreateRequest("ja", "en", new SourceRequest(SourceKind.LINK, null, null, SUPPORTED_EPISODE));
        int concurrentRequests = 8;
        var ready = new CountDownLatch(concurrentRequests);
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();

        try (var executor = Executors.newFixedThreadPool(concurrentRequests)) {
            // 実行: 同じ所有者で別々のキーを使用し、最初の上限行の作成も競合させる。
            for (int index = 0; index < concurrentRequests; index++) {
                String key = "quota-" + index;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(15, TimeUnit.SECONDS));
                    try {
                        jobs.create(84, key, request);
                        return true;
                    } catch (RequestFailure failure) {
                        assertEquals(HttpStatus.TOO_MANY_REQUESTS, failure.status);
                        assertEquals("QUOTA_EXCEEDED", failure.code);
                        return false;
                    }
                }));
            }
            try {
                assertTrue(ready.await(15, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }

            // 検証: 受理と永続状態が一致し、別のアカウントの枠は消費しない。
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(15, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(5, accepted);
            assertEquals(5, store.ownedJobIds(84).size());
            assertEquals(JobStatus.PREPARING, jobs.create(85, "other-owner", request).status());
        } finally {
            shutdownDatabase(dataSource);
        }
    }

    // アップロード中にアカウント削除が受理されると、受信済みデータを原本として公開しない。
    @Test
    void discardsLateUploadAfterAccountFenceCloses(@TempDir Path root) throws IOException, SQLException {
        // 準備: 実際の H2 ストアと隔離したファイル領域を使い、外部アカウント状態だけを制御する。
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:image_upload_fence_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new FileSystemResource("scripts/sql/20261122_image_translation.sql"))
                .execute(dataSource);
        var mapper = new ObjectMapper();
        var account = mock(AccountDataLifecycleAccess.class);
        var active = new AtomicBoolean(true);
        when(account.lockActive(56)).thenAnswer(ignored -> active.get());
        var store = new ImageTranslationStore(jdbc, mapper, new DataSourceTransactionManager(dataSource), account);
        var clients = new StaticListableBeanFactory().getBeanProvider(S3Client.class);
        var storage = new ImageTranslationAssets("http://127.0.0.1:18422", "fixture-secret-0123456789-0123456789",
                root.toString(), "", clients, store);
        var jobs = new ImageTranslationJobs(store, storage, new ImageTranslationLinks(""), mapper);
        var file = new FileRequest("fixture", "fixture.png", "image/png", 1);
        var source = new SourceRequest(SourceKind.FILES, List.of(file), null, null);
        var request = new CreateRequest("ja", "en", source);

        try {
            UUID id = jobs.create(56, "late-upload", request).jobId();
            UUID object = store.find(id, false).inputs.getFirst().objectId();

            // 実行: 最後のバイトを読んだ後、オブジェクトの公開前にアカウント状態を切り替える。
            try (var stream = new ByteArrayInputStream(new byte[] {1}) {
                @Override
                public int read() {
                    active.set(false);
                    return super.read();
                }
            }) {
                RequestFailure failure = assertThrows(RequestFailure.class, () -> storage.put(id, object, stream, 1));
                assertEquals(HttpStatus.NOT_FOUND, failure.status);
            }

            // 検証: 原本の確定、既存ジョブの読み取り、新規ジョブ作成をすべて拒否する。
            assertFalse(storage.exists(id, object));
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(RequestFailure.class, () -> jobs.get(56, id)).status);
            assertEquals(HttpStatus.NOT_FOUND,
                    assertThrows(RequestFailure.class, () -> jobs.create(56, "after-delete", request)).status);
        } finally {
            shutdownDatabase(dataSource);
        }
    }

    // SHUTDOWN は発行元の H2 接続も閉じるため、実行後の警告取得を伴わない JDBC で終了する。
    private static void shutdownDatabase(DriverManagerDataSource dataSource) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    // 機能専用の所有者ロックを検証するため共有アカウント契約は有効として扱う。
    private static AccountDataLifecycleAccess activeAccount() {
        var account = mock(AccountDataLifecycleAccess.class);
        when(account.lockActive(anyLong())).thenReturn(true);
        return account;
    }

    // Java 標準 ImageIO で外部データを使わずに静止画像を作る。
    private static byte[] image(String format, int width, int height) throws IOException {
        try (var encoded = new ByteArrayOutputStream()) {
            var raster = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            assertTrue(ImageIO.write(raster, format, encoded));
            return encoded.toByteArray();
        }
    }

    // JPEG SOI の直後に EXIF orientation=6 の最小 APP1 セグメントを挿入する。
    private static byte[] withClockwiseQuarterTurn(byte[] jpeg) throws IOException {
        byte[] segment = ByteBuffer.allocate(36)
                .putShort((short) 0xffe1)
                .putShort((short) 34)
                .put("Exif".getBytes(StandardCharsets.US_ASCII))
                .putShort((short) 0)
                .putShort((short) 0x4d4d)
                .putShort((short) 42)
                .putInt(8)
                .putShort((short) 1)
                .putShort((short) 0x0112)
                .putShort((short) 3)
                .putInt(1)
                .putShort((short) 6)
                .putShort((short) 0)
                .putInt(0)
                .array();
        try (var encoded = new ByteArrayOutputStream()) {
            encoded.write(jpeg, 0, 2);
            encoded.write(segment);
            encoded.write(jpeg, 2, jpeg.length - 2);
            return encoded.toByteArray();
        }
    }

    // 公開する翻訳・座標の形を保持しつつ、狙った値だけを差し替えられる応答を作る。
    private static JsonNode pageResponse(int right, int order, int width) throws JsonProcessingException {
        return new ObjectMapper().readTree("""
                {
                  "state":"READY","widthPx":%d,"heightPx":120,"ocrContractVersion":1,
                  "segments":[{
                    "segmentId":"segment-0","readingOrder":%d,
                    "sourceText":"こんにちは。","translatedText":"Hello.","confidence":null,
                    "polygon":[[10,20],[%d,20],[%d,40],[10,40]],
                    "writingMode":"horizontal-ltr","needsReview":false
                  }]
                }
                """.formatted(width, order, right, right));
    }
}
