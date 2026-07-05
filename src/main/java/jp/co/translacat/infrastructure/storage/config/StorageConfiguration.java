package jp.co.translacat.infrastructure.storage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            prefix = "translacat.storage",
            name = "type",
            havingValue = "s3"
    )
    public S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();

        requireText(s3.getEndpoint(), "STORAGE_S3_ENDPOINT");
        requireText(s3.getRegion(), "STORAGE_S3_REGION");
        requireText(s3.getBucket(), "STORAGE_S3_BUCKET");
        requireText(s3.getAccessKey(), "STORAGE_S3_ACCESS_KEY");
        requireText(s3.getSecretKey(), "STORAGE_S3_SECRET_KEY");
        requireText(
                s3.getPublicBaseUrl(),
                "STORAGE_S3_PUBLIC_BASE_URL"
        );

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3.getAccessKey(),
                s3.getSecretKey()
        );

        S3Configuration serviceConfiguration =
                S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build();

        return S3Client.builder()
                .endpointOverride(URI.create(s3.getEndpoint()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(credentials)
                )
                .region(Region.of(s3.getRegion()))
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    private void requireText(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    environmentName + " 설정이 필요합니다."
            );
        }
    }
}
