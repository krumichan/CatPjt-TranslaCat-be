import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Response;

/** 承認済みの運用環境で固定範囲を一度だけ検証する独立プロセス。 */
public final class R2ImageVerificationSdk implements R2ImageVerification.Storage {
    private static final String CANDIDATE_BUCKET_ENV = "CLOUDFLARE_R2_BOARD_BUCKET";
    private static final String ENDPOINT_ENV = "CLOUDFLARE_R2_ENDPOINT";
    private static final String REGION_ENV = "CLOUDFLARE_R2_REGION";
    private static final String ACCESS_KEY_ENV = "CLOUDFLARE_R2_ACCESS_KEY_ID";
    private static final String SECRET_KEY_ENV = "CLOUDFLARE_R2_SECRET_ACCESS_KEY";
    private static final Pattern R2_HOST = Pattern.compile(
            "[a-f0-9]{32}(?:[.](?:eu|fedramp))?[.]r2[.]cloudflarestorage[.]com");
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(20);

    private final S3Client s3;

    private R2ImageVerificationSdk(S3Client s3) {
        this.s3 = s3;
    }

    /**
     * 検証済み素材だけを使い、固定の結果コードで終了する。専用 JVM から呼び出す。
     *
     * @param args 001.jpg、002.jpg、003.jpg の順のローカルファイルパス。
     */
    public static void main(String[] args) {
        // SDK のログ・例外詳細を出力せず、検証コードと固定 UUID だけを報告する。
        PrintStream summary = System.out;
        PrintStream silent = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        System.setOut(silent);
        System.setErr(silent);
        System.exit(run(args, summary));
    }

    // 入力が確定するまで資格情報に触れず、SDK の詳細を出力せずに結果を返す。
    private static int run(String[] args, PrintStream summary) {
        Map<String, byte[]> jpegByKey;
        try {
            jpegByKey = readJpegs(args);
        } catch (IOException | RuntimeException failure) {
            summary.println("outcome=INPUT_REJECTED");
            return 2;
        }

        // SDK を閉じてから最終結果を確定する。後処理失敗時も観測済みの UUID は報告する。
        R2ImageVerification.Report report = null;
        boolean closeFailed = false;
        try (S3Client s3 = openClient()) {
            report = R2ImageVerification.verify(
                    R2ImageVerification.BUCKET, R2ImageVerification.PREFIX, jpegByKey,
                    new R2ImageVerificationSdk(s3));
        } catch (RuntimeException | LinkageError failure) {
            if (report == null) {
                summary.println("outcome=SDK_SETUP_OR_EXECUTION_FAILED");
                return 2;
            }
            closeFailed = true;
        }

        if (closeFailed) {
            summary.println("outcome=SDK_CLOSE_FAILED");
            summary.println("probeOutcome=" + report.outcome());
        } else {
            summary.println("outcome=" + report.outcome());
        }
        summary.println("confirmedKeys=" + report.confirmedKeys());
        summary.println("deletedKeys=" + report.deletedKeys());
        summary.println("uncertainKey=" + (report.uncertainKey() == null ? "none" : report.uncertainKey()));
        summary.println("finalListEmpty=" + report.finalListEmpty());
        return closeFailed ? 2 : (report.passed() ? 0 : 1);
    }

    // 各ファイルを固定順序で読み、上限を超える内容やピン不一致を受け付けない。
    private static Map<String, byte[]> readJpegs(String[] args) throws IOException {
        if (args.length != R2ImageVerification.FIXTURES.size()) {
            throw new IOException("Expected exactly three JPEG paths");
        }

        Map<String, byte[]> jpegByKey = new HashMap<>();
        for (int index = 0; index < args.length; index++) {
            R2ImageVerification.Fixture fixture = R2ImageVerification.FIXTURES.get(index);
            try (InputStream input = Files.newInputStream(Path.of(args[index]))) {
                byte[] bytes = input.readNBytes(fixture.sizeBytes() + 1);
                if (!R2ImageVerification.matchesFixture(fixture, bytes)) {
                    throw new IOException("JPEG pin mismatch");
                }
                jpegByKey.put(fixture.key(), bytes);
            }
        }
        return jpegByKey;
    }

    // 運用 BE に渡された R2 環境だけを使い、認証情報の探索と SDK の再試行を行わない。
    private static S3Client openClient() {
        if (!R2ImageVerification.BUCKET.equals(System.getenv(CANDIDATE_BUCKET_ENV))) {
            throw new IllegalStateException("Candidate bucket scope mismatch");
        }
        URI endpoint = r2Endpoint(required(ENDPOINT_ENV));
        String region = System.getenv(REGION_ENV);
        if (region == null || region.isEmpty()) {
            region = "auto";
        }
        if (!"auto".equals(region) && !"us-east-1".equals(region)) {
            throw new IllegalStateException("Unsupported R2 signing region");
        }

        // 既存 BE と同じ path-style と非 chunked 署名で、転送後に SHA-256 を直接照合する。
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .httpClientBuilder(Apache5HttpClient.builder()
                        .connectionTimeout(ATTEMPT_TIMEOUT)
                        .socketTimeout(ATTEMPT_TIMEOUT))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(required(ACCESS_KEY_ENV), required(SECRET_KEY_ENV))))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(options -> options
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())
                        .apiCallAttemptTimeout(ATTEMPT_TIMEOUT)
                        .apiCallTimeout(CALL_TIMEOUT))
                .build();
    }

    // R2 のアカウント用 HTTPS オリジンだけを許可する。
    private static URI r2Endpoint(String value) {
        URI endpoint = URI.create(value);
        if (!"https".equals(endpoint.getScheme())
                || endpoint.getHost() == null
                || !R2_HOST.matcher(endpoint.getHost()).matches()
                || endpoint.getPort() != -1
                || endpoint.getRawUserInfo() != null
                || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null
                || !("".equals(endpoint.getRawPath()) || "/".equals(endpoint.getRawPath()))) {
            throw new IllegalStateException("Invalid R2 endpoint");
        }
        return endpoint;
    }

    // 必須環境値を一つずつ取得し、値を失敗メッセージに含めない。
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required R2 environment value absent");
        }
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public R2ImageVerification.Listing list() throws IOException {
        var response = s3.listObjectsV2(request -> request
                .bucket(R2ImageVerification.BUCKET)
                .prefix(R2ImageVerification.PREFIX)
                .maxKeys(1));
        requireSuccessful(response);

        // プレフィックスや件数を確認できない応答は空として扱わない。
        if (!R2ImageVerification.PREFIX.equals(response.prefix())
                || response.keyCount() == null
                || response.keyCount() > 1
                || response.keyCount() != response.contents().size()
                || !response.commonPrefixes().isEmpty()
                || response.isTruncated() == null
                || (!response.isTruncated() && response.nextContinuationToken() != null)) {
            throw new IOException("Unreliable scoped LIST response");
        }
        return new R2ImageVerification.Listing(response.keyCount(), response.isTruncated());
    }

    /** {@inheritDoc} */
    @Override
    public R2ImageVerification.Put put(String key, byte[] bytes) {
        String fullKey = scopedKey(key);
        try {
            var response = s3.putObject(request -> request
                    .bucket(R2ImageVerification.BUCKET)
                    .key(fullKey)
                    .contentType(R2ImageVerification.CONTENT_TYPE)
                    .ifNoneMatch("*"), RequestBody.fromBytes(bytes));
            return response.sdkHttpResponse().isSuccessful()
                    ? R2ImageVerification.Put.CREATED : R2ImageVerification.Put.UNKNOWN;
        } catch (S3Exception failure) {
            return failure.statusCode() == 412
                    ? R2ImageVerification.Put.CONFLICT : R2ImageVerification.Put.UNKNOWN;
        }
    }

    /** {@inheritDoc} */
    @Override
    public R2ImageVerification.Head head(String key) throws IOException {
        var response = s3.headObject(request -> request.bucket(R2ImageVerification.BUCKET).key(scopedKey(key)));
        requireSuccessful(response);
        if (response.contentLength() == null) {
            throw new IOException("HEAD did not return a size");
        }
        return new R2ImageVerification.Head(response.contentLength(), response.contentType());
    }

    /** {@inheritDoc} */
    @Override
    public byte[] get(String key, int maxBytes) throws IOException {
        try (ResponseInputStream<GetObjectResponse> body = s3.getObject(request -> request
                .bucket(R2ImageVerification.BUCKET)
                .key(scopedKey(key)))) {
            // 不一致や読み取り失敗時は残りの応答を読み切らずに接続を止める。
            try {
                requireSuccessful(body.response());
                Long length = body.response().contentLength();
                if (length == null || length != maxBytes - 1L) {
                    throw new IOException("GET size does not match the pinned length");
                }
                byte[] bytes = body.readNBytes(maxBytes);
                if (bytes.length == maxBytes) {
                    throw new IOException("GET body exceeds read limit");
                }
                return bytes;
            } catch (IOException | RuntimeException failure) {
                body.abort();
                throw failure;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void delete(String key) throws IOException {
        requireSuccessful(s3.deleteObject(request -> request
                .bucket(R2ImageVerification.BUCKET)
                .key(scopedKey(key))));
    }

    // 不明なステータスを成功として扱わない。
    private static void requireSuccessful(S3Response response) throws IOException {
        if (response.sdkHttpResponse() == null || !response.sdkHttpResponse().isSuccessful()) {
            throw new IOException("SDK response did not confirm success");
        }
    }

    // SDK 側でも三つの UUID だけを固定プレフィックスの配下に割り当てる。
    private static String scopedKey(String key) {
        if (R2ImageVerification.FIXTURES.stream().noneMatch(fixture -> fixture.key().equals(key))) {
            throw new IllegalArgumentException("Object key out of scope");
        }
        return R2ImageVerification.PREFIX + key;
    }
}
