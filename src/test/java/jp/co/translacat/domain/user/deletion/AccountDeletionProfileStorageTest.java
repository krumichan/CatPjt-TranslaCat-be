package jp.co.translacat.domain.user.deletion;

import jp.co.translacat.infrastructure.storage.config.StorageProperties;
import jp.co.translacat.infrastructure.storage.local.LocalImageStorageAdapter;
import jp.co.translacat.infrastructure.storage.s3.S3CompatibleImageStorageAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountDeletionProfileStorageTest {
    @TempDir Path root;

    @Test
    void localCleanupRemovesCurrentAndOrphanProfileObjectsButPreservesOtherAccountsAndPrivateAudio() throws Exception {
        // 준비
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setRootPath(root.resolve("public").toString());
        properties.getLocal().setPublicBaseUrl("https://images.example.invalid");
        var storage = new LocalImageStorageAdapter(properties);
        Path own = root.resolve("public/user-profile/1/profile/old.jpg");
        Path other = root.resolve("public/user-profile/10/profile/current.jpg");
        Path audio = root.resolve("public-private-audio/novel/audio/v1/synthetic.wav");
        for (Path file : java.util.List.of(own, other, audio)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "synthetic");
        }

        // 실행
        assertThat(storage.deleteUserProfileObjects(1)).isTrue();

        // 검증: 숫자 계정 prefix의 slash 경계를 지키고 별도 privateAudioRoot에는 접근하지 않는다.
        assertThat(own).doesNotExist();
        assertThat(other).exists();
        assertThat(audio).exists();
        assertThat(storage.deleteUserProfileObjects(1)).isTrue();
    }

    @Test
    void s3CleanupUsesExactAccountPrefixAndVerifiesNoRemainingObjects() {
        // 준비
        S3Client s3 = mock(S3Client.class);
        StorageProperties properties = new StorageProperties();
        properties.getS3().setBucket("synthetic-bucket");
        properties.getS3().setPublicBaseUrl("https://images.example.invalid");
        var storage = new S3CompatibleImageStorageAdapter(s3, properties);
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(S3Object.builder().key("user-profile/1/profile/orphan.jpg").build()).build(),
                ListObjectsV2Response.builder().build());

        // 실행
        assertThat(storage.deleteUserProfileObjects(1)).isTrue();

        // 검증
        verify(s3, times(2)).listObjectsV2(argThat((ListObjectsV2Request request) -> request.prefix().equals("user-profile/1/")));
        verify(s3).deleteObject(argThat((DeleteObjectRequest request) -> request.key().equals("user-profile/1/profile/orphan.jpg")));
    }

    @Test
    void s3ForeignPrefixAndProviderFailureCannotBeTreatedAsDeletionSuccess() {
        // 준비
        S3Client s3 = mock(S3Client.class);
        StorageProperties properties = new StorageProperties();
        properties.getS3().setPublicBaseUrl("https://images.example.invalid");
        var storage = new S3CompatibleImageStorageAdapter(s3, properties);
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(S3Object.builder().key("user-profile/10/profile/other.jpg").build()).build());

        // 실행
        assertThatThrownBy(() -> storage.deleteUserProfileObjects(1)).isInstanceOf(IllegalStateException.class);

        // 검증
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(S3Exception.builder().statusCode(503).message("synthetic").build());
        assertThatThrownBy(() -> storage.deleteUserProfileObjects(1)).isInstanceOf(RuntimeException.class);
    }
}
