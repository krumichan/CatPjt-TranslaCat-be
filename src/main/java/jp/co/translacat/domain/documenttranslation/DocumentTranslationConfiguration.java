package jp.co.translacat.domain.documenttranslation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort;
import jp.co.translacat.domain.documenttranslation.format.OfficeDocumentProcessor;
import jp.co.translacat.domain.documenttranslation.translation.AiDocumentTranslationGateway;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** 공개 API를 유지하면서 저장, Office, AI 구현을 교체하는 문서 모듈 조립 지점이다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "translacat.document-translation.enabled", havingValue = "true")
class DocumentTranslationConfiguration {

    // 기존 데이터 소스를 쓰며 문서 DB의 트랜잭션 범위만 저장 어댑터로 넘긴다.
    @Bean
    DocumentJobRepository documentJobRepository(JdbcTemplate jdbc, PlatformTransactionManager manager,
            ObjectMapper mapper, DocumentObjectWrites writes) {
        return new DocumentJobRepository(jdbc, new TransactionTemplate(manager), mapper, writes);
    }

    // 사용 가능한 설정은 전용 비공개 버킷 또는 명시적인 개발 디렉터리다.
    @Bean
    DocumentFiles documentFiles(DocumentStorageAccess storage) {
        return storage.configured()
                .orElseThrow(() -> new IllegalStateException("Document translation needs a private object store"));
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
    DocumentTranslationService documentTranslationService(DocumentJobRepository repository, DocumentFiles files,
            DocumentObjectWrites writes) {
        return new DocumentTranslationService(repository, files, writes, Clock.systemUTC());
    }

    // 워커는 공유 스케줄 호출에서 별도 제한된 실행 풀로 작업을 넘긴다.
    @Bean(destroyMethod = "close")
    DocumentTranslationWorker documentTranslationWorker(DocumentJobRepository repository, DocumentFiles files,
            DocumentFormatPort formats, DocumentTranslationGateway translations, ObjectMapper mapper,
            DocumentObjectWrites writes) {
        return new DocumentTranslationWorker(repository, files, formats, translations, mapper, writes,
                Clock.systemUTC());
    }
}
