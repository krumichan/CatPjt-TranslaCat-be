package jp.co.translacat.domain.documenttranslation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort;
import jp.co.translacat.domain.documenttranslation.format.OfficeDocumentProcessor;
import jp.co.translacat.domain.documenttranslation.translation.AiDocumentTranslationGateway;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;

/** 공개 API를 유지하면서 저장, Office, AI 구현을 교체하는 문서 모듈 조립 지점이다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "translacat.document-translation.enabled", havingValue = "true")
class DocumentTranslationConfiguration {

    // 현재 톰캣 설정을 바꾸지 않고 문서 경로가 사용하는 유한 NIO 유휴 시간을 확인한다.
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> documentTransferConnectorPolicy() {
        return factory -> factory.addConnectorCustomizers(DocumentTranslationConfiguration::verifyConnector);
    }

    // 톰캣 NIO는 연결 시간을 소켓 읽기·쓰기 유휴 시간에 동일하게 설정한다.
    private static void verifyConnector(Connector connector) {
        if (!(connector.getProtocolHandler() instanceof Http11NioProtocol protocol)) {
            throw new IllegalStateException("Document translation requires a bounded HTTP/1.1 NIO connector");
        }
        int idle = protocol.getConnectionTimeout();
        int upload = protocol.getDisableUploadTimeout() ? idle : protocol.getConnectionUploadTimeout();
        if (idle <= 0 || idle > 60_000 || upload <= 0 || upload > 60_000) {
            throw new IllegalStateException("Document transfer requires positive socket limits within 60 seconds");
        }
    }

    // 기존 데이터 소스를 쓰며 문서 DB의 트랜잭션 범위만 저장 어댑터로 넘긴다.
    @Bean
    DocumentJobRepository documentJobRepository(JdbcTemplate jdbc, PlatformTransactionManager manager,
            ObjectMapper mapper, DocumentObjectWrites writes) {
        return new DocumentJobRepository(jdbc, new TransactionTemplate(manager), mapper, writes);
    }

    // 전용 경로의 잠금과 이전 임시 파일 정리가 끝난 뒤 작업을 접수할 수 있다.
    @Bean(destroyMethod = "close")
    DocumentTransientFiles documentTransientFiles(DocumentJobRepository repository,
            @Value("${translacat.document-translation.temporary-directory:}") String configured,
            @Value("${translacat.document-translation.runtime-profile:volatile}") String runtimeProfile,
            Environment environment) throws IOException {
        Path root = temporaryRoot(configured, runtimeProfile, environment);
        var files = new DocumentTransientFiles(root);
        try {
            var now = Clock.systemUTC().instant();
            repository.invalidateEphemeralJobs(now);
            repository.fenceLegacyJobs(now);
            return files;
        } catch (RuntimeException startupFailed) {
            try {
                files.close();
            } catch (IOException closeFailed) {
                startupFailed.addSuppressed(closeFailed);
            }
            throw startupFailed;
        }
    }

    // 운영 기본값은 명시된 휘발성 볼륨이며 Windows 검수 예외는 운영 프로필과 함께 쓸 수 없다.
    private static Path temporaryRoot(String configured, String runtimeProfile, Environment environment)
            throws IOException {
        if (configured.isBlank()) {
            throw new IllegalStateException("Document translation requires an explicit working volume");
        }
        Path root = Path.of(configured);
        if (!root.isAbsolute() || !root.normalize().equals(root) || root.getParent() == null) {
            throw new IllegalStateException("Document translation requires an absolute dedicated workspace");
        }
        boolean windowsQa = "isolated-windows-qa".equals(runtimeProfile)
                && System.getProperty("os.name").startsWith("Windows")
                && !environment.matchesProfiles("prod", "production");
        if (windowsQa) {
            return root;
        }
        if (!"volatile".equals(runtimeProfile)) {
            throw new IllegalStateException("Unsupported document runtime profile");
        }

        Path mount = Files.exists(root, LinkOption.NOFOLLOW_LINKS) ? root : root.getParent();
        String type = Files.getFileStore(mount).type().toLowerCase(Locale.ROOT);
        if (!"tmpfs".equals(type) && !"ramfs".equals(type)) {
            throw new IllegalStateException("Document translation requires a volatile working volume");
        }
        return root;
    }

    // 구조 처리기는 다른 모듈의 작업·인증 데이터에 의존하지 않는다.
    @Bean
    DocumentFormatPort documentFormatPort() {
        return new OfficeDocumentProcessor();
    }

    // AI 실행 URL과 서비스 인증은 기존 설정 계약을 사용한다.
    @Bean
    DocumentTranslationGateway documentTranslationGateway(ObjectMapper mapper,
            @Value("${ai-server.url:}") String baseUrl, @Value("${ai-server.api-key:}") String apiKey,
            @Value("${translacat.document-translation.expected-model:}") String expectedModel) {
        return new AiDocumentTranslationGateway(mapper, baseUrl, apiKey, expectedModel);
    }

    // HTTP 서비스는 저장과 시간의 경계만 사용한다.
    @Bean
    DocumentTranslationService documentTranslationService(DocumentJobRepository repository, DocumentTransientFiles files,
            DocumentObjectWrites writes) {
        return new DocumentTranslationService(repository, files, writes, Clock.systemUTC());
    }

    // 워커는 공유 스케줄 호출에서 별도 제한된 실행 풀로 작업을 넘긴다.
    @Bean(destroyMethod = "close")
    DocumentTranslationWorker documentTranslationWorker(DocumentJobRepository repository, DocumentTransientFiles files,
            DocumentFormatPort formats, DocumentTranslationGateway translations) {
        return new DocumentTranslationWorker(repository, files, formats, translations, Clock.systemUTC());
    }
}
