package jp.co.translacat.infrastructure.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "translacat.storage")
public class StorageProperties {

    private String type = "local";
    private Local local = new Local();
    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class Local {
        private String rootPath = "./storage";
        private String publicBaseUrl =
                "http://localhost:8080/api/v1/public/storage";
    }

    @Getter
    @Setter
    public static class S3 {
        private String endpoint;
        private String region = "auto";
        private String bucket;
        private String accessKey;
        private String secretKey;
        private String publicBaseUrl;
    }
}
