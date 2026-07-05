package jp.co.translacat.infrastructure.storage.local;

import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.infrastructure.storage.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void 저장하고_삭제할_수_있다() throws Exception {
        StorageProperties properties = properties();
        LocalImageStorageAdapter adapter =
                new LocalImageStorageAdapter(properties);

        String objectKey =
                "user-profile/1/profile/test.png";

        adapter.store(new ImageStorageUpload(
                objectKey,
                "image/png",
                new byte[]{1, 2, 3}
        ));

        Path stored = tempDir.resolve(objectKey);
        assertThat(Files.exists(stored)).isTrue();

        adapter.delete(objectKey);
        assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    void root_밖으로_벗어나는_경로를_거부한다() {
        StorageProperties properties = properties();
        LocalImageStorageAdapter adapter =
                new LocalImageStorageAdapter(properties);

        assertThatThrownBy(() ->
                adapter.resolveSafePath("../escape.png")
        ).isInstanceOf(RuntimeException.class);
    }

    private StorageProperties properties() {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setRootPath(tempDir.toString());
        properties.getLocal().setPublicBaseUrl(
                "http://localhost:8080/api/v1/public/storage"
        );
        return properties;
    }
}
