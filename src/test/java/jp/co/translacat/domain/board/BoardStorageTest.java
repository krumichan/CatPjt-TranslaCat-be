package jp.co.translacat.domain.board;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardStorageTest {

    @Test
    void signedPartUrlBindsTheExactContentLength() {
        // 준비: URL 서명만 실행하므로 R2 자격증명이나 네트워크가 필요 없다.
        try (var signer = S3Presigner.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create("https://example.invalid"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            BoardStorage storage = storage(signer, "private-board");

            // 실행
            String url = storage.signPart(UUID.randomUUID(), "upload-1", 2, 30_000_000);

            // 검증
            assertThat(url).startsWith("https://example.invalid/private-board/boards/");
            assertThat(url).contains("X-Amz-SignedHeaders=content-length%3Bhost", "partNumber=2", "uploadId=upload-1");
        }
    }

    @Test
    void refusesToUseAnExistingPublicBucket() {
        // 준비
        BoardStorage storage = storage(mock(S3Presigner.class), "public-profile");

        // 실행 및 검증
        assertThatThrownBy(() -> storage.signPart(UUID.randomUUID(), "upload-1", 1, 10))
                .hasMessage("BOARD_STORAGE_UNAVAILABLE");
    }

    private static BoardStorage storage(S3Presigner signer, String bucket) {
        @SuppressWarnings("unchecked")
        ObjectProvider<S3Presigner> presigners = mock(ObjectProvider.class);
        when(presigners.getIfAvailable()).thenReturn(signer);
        @SuppressWarnings("unchecked")
        ObjectProvider<S3Client> clients = mock(ObjectProvider.class);
        BoardStorage storage = new BoardStorage(clients, presigners);
        ReflectionTestUtils.setField(storage, "bucket", bucket);
        ReflectionTestUtils.setField(storage, "publicBucket", "public-profile");
        return storage;
    }
}
