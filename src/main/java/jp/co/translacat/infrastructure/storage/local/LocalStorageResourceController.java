package jp.co.translacat.infrastructure.storage.local;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@RestController
@ConditionalOnProperty(
        prefix = "translacat.storage",
        name = "type",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalStorageResourceController {

    private static final String PUBLIC_PREFIX =
            "/api/v1/public/storage/";

    private final LocalImageStorageAdapter localImageStorageAdapter;

    public LocalStorageResourceController(
            LocalImageStorageAdapter localImageStorageAdapter
    ) {
        this.localImageStorageAdapter = localImageStorageAdapter;
    }

    @GetMapping("/api/v1/public/storage/**")
    public ResponseEntity<Resource> getObject(
            HttpServletRequest request
    ) throws Exception {
        String requestUri = request.getRequestURI();

        if (!requestUri.startsWith(PUBLIC_PREFIX)) {
            return ResponseEntity.notFound().build();
        }

        String encodedObjectKey =
                requestUri.substring(PUBLIC_PREFIX.length());

        String objectKey = URLDecoder.decode(
                encodedObjectKey,
                StandardCharsets.UTF_8
        );

        Path path = localImageStorageAdapter.resolveSafePath(objectKey);

        if (!Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }

        String detectedContentType = Files.probeContentType(path);
        MediaType mediaType = detectedContentType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(detectedContentType);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(
                        CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic()
                )
                .body(new PathResource(path));
    }
}
