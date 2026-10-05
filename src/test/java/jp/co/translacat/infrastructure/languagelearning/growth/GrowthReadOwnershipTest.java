package jp.co.translacat.infrastructure.languagelearning.growth;

import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GrowthReadOwnershipTest {
    @Test
    void currentReadConfigurationRequiresNoCoreDatabaseOrMigrationWorker() {
        // 준비: 현재 조회는 외부 DTO용 Gateway이며 이관 DB·dispatcher는 조립하지 않는다.
        var runner = new ApplicationContextRunner().withUserConfiguration(GrowthConfiguration.class)
                .withBean("languageLearningRestClient", RestClient.class,
                        () -> RestClient.builder().baseUrl("https://ll.fixture.invalid").build())
                .withBean(LanguageLearningInternalJwtProvider.class,
                        () -> mock(LanguageLearningInternalJwtProvider.class))
                .withBean(ObjectMapper.class, ObjectMapper::new);

        // 실행 및 검증
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(GrowthReadGateway.class));
            assertFalse(context.containsBean("growthOutboxStore"));
            assertFalse(context.containsBean("growthDispatcher"));
        });
    }

    @Test
    void languageLearningSourcesDoNotDependOnRetiredTransferOwners() throws Exception {
        // 준비: 언어학습 운영 소스만 감사하고 다른 제품의 JDBC·배치는 검사 범위에서 제외한다.
        var roots = List.of(Path.of("src/main/java/jp/co/translacat/domain/languagelearning"),
                Path.of("src/main/java/jp/co/translacat/infrastructure/languagelearning"));
        var retired = Pattern.compile("\\b(GrowthOutboxStore|GrowthDispatcher|GrowthEnvelope|GrowthAcknowledgement|"
                + "ResultOutboxStore|ResultOutboxDispatcher|ResultJournalClient|ResultEnvelope|ResultAcknowledgement|"
                + "SelectionDeliveryRequestDto|SelectionDeliveryResponseDto|JdbcTemplate|JpaRepository)\\b");

        // 실행 및 검증: 현행 Gateway와 외부 DTO를 남기고 구 Core 저장·전달 의존성을 제거한다.
        for (var root : roots) {
            try (var paths = Files.walk(root)) {
                for (var path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    assertFalse(retired.matcher(Files.readString(path)).find(), path.toString());
                }
            }
        }
        assertTrue(Files.exists(roots.get(0).resolve("profile/dto/response/ProfileResponseDto.java")));
        assertTrue(Files.exists(roots.get(1).resolve("growth/RemoteGrowthGateway.java")));
    }
}
