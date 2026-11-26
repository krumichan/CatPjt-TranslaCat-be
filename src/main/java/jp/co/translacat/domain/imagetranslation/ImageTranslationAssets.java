package jp.co.translacat.domain.imagetranslation;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.JobStatus;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.RequestFailure;

@Component
@ConditionalOnProperty(prefix = "translacat.image-translation", name = "asset-secret")
class ImageTranslationAssets {
    private static final String JOBS = "/api/v1/image-translation/jobs";
    private static final String PREFIX = "image-translation/";
    private final Path localRoot;
    private final Path scratch;
    private final String publicOrigin;
    private final byte[] secret;
    private final String bucket;
    private final S3Client s3;
    private final ImageTranslationStore store;

    // 프로필의 공개 객체와 분리해 전용 비공개 버킷 또는 격리 파일 사본을 사용한다.
    ImageTranslationAssets(
            @Value("${translacat.image-translation.public-origin}") String publicOrigin,
            @Value("${translacat.image-translation.asset-secret}") String secret,
            @Value("${translacat.image-translation.local-root:}") String localRoot,
            @Value("${translacat.image-translation.private-bucket:}") String bucket,
            ObjectProvider<S3Client> clients, ImageTranslationStore store) throws IOException {
        this.publicOrigin = requireOrigin(publicOrigin);
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.store = store;
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("Image translation asset secret requires at least 32 bytes");
        }

        // 원본은 설정된 저장소에, 임시 전송 파일은 서버의 비공개 작업 디렉터리에 둔다.
        this.bucket = bucket;
        this.s3 = bucket.isBlank() ? null : clients.getObject();
        if (s3 == null && localRoot.isBlank()) {
            throw new IllegalArgumentException("Image translation requires a private bucket or an explicit local root");
        }
        this.localRoot = localRoot.isBlank() ? null : Path.of(localRoot).toAbsolutePath().normalize();
        this.scratch = this.localRoot == null
                ? Files.createTempDirectory("translacat-image-translation-")
                : Files.createDirectories(this.localRoot.resolve(".incoming"));
    }

    // 만료 시각·객체 ID·동작을 하나의 짧은 권한으로 서명해 절대 주소를 발급한다.
    String ticket(String method, UUID job, UUID object, long deadline) {
        String action = method.equals("PUT") ? "upload" : "original";
        return publicOrigin + JOBS + "/" + job + "/files/" + object + "/" + action
                + "?expires=" + deadline + "&ticket=" + signature(method, job, object, deadline);
    }

    // 대상 동작의 서명이 일치하고 만료 전인지 입력 길이와 함께 확인한다.
    boolean permits(String method, UUID job, UUID object, long deadline, String supplied) {
        if (deadline <= Instant.now().toEpochMilli() || supplied == null || supplied.length() != 43) {
            return false;
        }
        return MessageDigest.isEqual(signature(method, job, object, deadline).getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII));
    }

    // 완전히 전송한 동일 크기만 저장하며, 이미 고정된 원본은 항상 그대로 유지한다.
    void put(UUID job, UUID object, InputStream source, long expectedBytes) throws IOException {
        Path temp = Files.createTempFile(scratch, "image-", ".part");
        try {
            // 저장 전에 정확한 바이트 길이를 확인한다. 초과분은 메모리나 파일에 담지 않는다.
            try (var output = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                long remaining = expectedBytes;
                while (remaining > 0) {
                    int read = source.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) {
                        throw new IllegalArgumentException("Unexpected upload size");
                    }
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
                if (source.read() != -1) {
                    throw new IllegalArgumentException("Unexpected upload size");
                }
            }

            // 본문 전송은 잠금 밖에서 끝내고, 최종 객체 공개만 계정 삭제와 직렬화한다.
            publishWhileActive(job, object, temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // 삭제 접수 및 취소 이후의 작업은 이미 받은 본문을 남기지 않는다.
    private void publishWhileActive(UUID job, UUID object, Path incoming) throws IOException {
        try {
            store.transaction(() -> {
                var current = store.lockWritableJob(job);
                if (current == null || current.expiresAt <= ImageTranslationJobs.now()
                        || current.status == JobStatus.CANCELLED || current.status == JobStatus.EXPIRED) {
                    throw new RequestFailure(HttpStatus.NOT_FOUND, "NOT_FOUND");
                }
                try {
                    publish(job, object, incoming);
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
                return null;
            });
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    // 보호 중인 객체를 원자적으로 생성한다. 이미 존재하면 같은 바이트만 허용한다.
    private void publish(UUID job, UUID object, Path incoming) throws IOException {
        if (s3 != null) {
            putS3(job, object, incoming);
            return;
        }
        Path target = localPath(job, object);
        Files.createDirectories(target.getParent());
        try {
            Files.createLink(target, incoming);
        } catch (FileAlreadyExistsException ignored) {
            requireMatchingCopy(job, object, incoming);
        }
    }

    // 작업의 객체가 이미 완전히 준비됐는지 확인한다.
    boolean exists(UUID job, UUID object) {
        if (s3 == null) {
            return Files.isRegularFile(localPath(job, object));
        }
        try {
            s3.headObject(request -> request.bucket(bucket).key(key(job, object)));
            return true;
        } catch (S3Exception error) {
            if (error.statusCode() == 404) {
                return false;
            }
            throw error;
        }
    }

    // 소유권과 작업 상태를 확인한 호출자가 객체를 스트리밍한다.
    InputStream open(UUID job, UUID object) throws IOException {
        if (s3 == null) {
            return Files.newInputStream(localPath(job, object));
        }
        return s3.getObject(request -> request.bucket(bucket).key(key(job, object)));
    }

    // 만료된 작업의 전용 경로만 제거하며, 이미 없는 객체의 제거도 성공한다.
    void removeJob(UUID job) throws IOException {
        if (s3 != null) {
            String prefix = PREFIX + job + "/";
            for (var page : s3.listObjectsV2Paginator(request -> request.bucket(bucket).prefix(prefix))) {
                for (var object : page.contents()) {
                    if (object.key().startsWith(prefix)) {
                        s3.deleteObject(request -> request.bucket(bucket).key(object.key()));
                    }
                }
            }
            return;
        }

        Path jobRoot = localRoot.resolve(job.toString()).normalize();
        if (jobRoot.getParent().equals(localRoot) && Files.isDirectory(jobRoot)) {
            try (var paths = Files.walk(jobRoot)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    // 내용을 기준으로 해시하고 원본의 표시용 이름과 경로는 입력에 넣지 않는다.
    static String hash(byte[] value) {
        return HexFormat.of().formatHex(digest().digest(value));
    }

    // 재시도된 쓰기는 기존 객체의 전체 해시와 비교해 같은 요청만 허용한다.
    private void requireMatchingCopy(UUID job, UUID object, Path incoming) throws IOException {
        try (InputStream prior = open(job, object); InputStream replacement = Files.newInputStream(incoming)) {
            if (!MessageDigest.isEqual(digest(prior), digest(replacement))) {
                throw new IllegalArgumentException("Upload object is already sealed");
            }
        }
    }

    // 저장소에서도 이미 존재하는 객체의 덮어쓰기를 거절한다.
    private void putS3(UUID job, UUID object, Path incoming) throws IOException {
        try {
            s3.putObject(request -> request.bucket(bucket).key(key(job, object)).ifNoneMatch("*"),
                    RequestBody.fromFile(incoming));
        } catch (S3Exception error) {
            if (error.statusCode() != 412) {
                throw error;
            }
            requireMatchingCopy(job, object, incoming);
        }
    }

    // 전체 파일을 메모리에 올리지 않고 입력을 해시한다.
    private static byte[] digest(InputStream input) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
        return digest.digest();
    }

    // 런타임이 보장하는 SHA-256을 사용한다.
    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    // 외부 입력을 객체 경로에 직접 사용하지 않는다.
    private Path localPath(UUID job, UUID object) {
        return localRoot.resolve(job.toString()).resolve(object.toString());
    }

    private static String key(UUID job, UUID object) {
        return PREFIX + job + "/" + object;
    }

    // 호출마다 독립적인 HMAC 인스턴스로 해당 권한의 바이트를 서명한다.
    private String signature(String method, UUID job, UUID object, long deadline) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String value = method + "/" + job + "/" + object + "/" + deadline;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Image ticket signing is unavailable", error);
        }
    }

    // 서명 URL은 요청 Host 헤더 대신 설정된 HTTPS origin 또는 개발 loopback에만 발급한다.
    private static String requireOrigin(String value) {
        URI origin = URI.create(value);
        boolean https = "https".equals(origin.getScheme());
        boolean loopback = "http".equals(origin.getScheme())
                && ("127.0.0.1".equals(origin.getHost()) || "localhost".equals(origin.getHost()));
        if ((!https && !loopback) || origin.getHost() == null || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null || origin.getRawFragment() != null
                || (!origin.getRawPath().isEmpty() && !origin.getRawPath().equals("/"))) {
            throw new IllegalArgumentException("Image translation public origin is invalid");
        }
        return origin.getScheme() + "://" + origin.getRawAuthority();
    }
}
