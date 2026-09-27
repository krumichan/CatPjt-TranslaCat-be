package jp.co.translacat.domain.languagelearning.listening;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListeningOwnershipBoundaryTest {
    @Test
    void externalFacadesDelegateToLlAndRetiredBusinessOwnersAreAbsent() throws Exception {
        // 준비: 외부 Controller와 DTO는 유지하고 업무 소유자만 LL로 이행한다.
        Path source = Path.of("src/main/java/jp/co/translacat");
        Path feature = source.resolve("domain/languagelearning/listening");
        List<String> retired = List.of(
                "ListeningGenerationService", "ListeningGenerationWorker", "ListeningEvaluationWorker",
                "ListeningDailySetRepository", "ListeningSessionRepository", "ListeningItemRepository",
                "ListeningTaskEvaluationRepository", "ListeningTaskResponseRepository",
                "AiServerListeningClient", "ListeningAiClient", "AiListeningContract",
                "ListeningAudioStoragePort", "ListeningDurationPolicy");
        Pattern references = Pattern.compile("\\b(" + String.join("|", retired) + ")\\b");

        // 실행 및 검증: 실행 가능한 Java 소스에 제거한 저장·작업자·AI 업무 경로가 남지 않는다.
        try (var paths = Files.walk(source)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertFalse(references.matcher(Files.readString(path)).find(), path.toString());
            }
        }
        for (String facade : List.of("daily/facade/ListeningDailySetFacade.java",
                "session/facade/ListeningSessionFacade.java", "dashboard/facade/ListeningDashboardFacade.java")) {
            assertTrue(Files.readString(feature.resolve(facade)).contains("ListeningGateway"), facade);
        }
        assertTrue(Files.exists(feature.resolve("controller/ListeningSessionController.java")));
        assertTrue(Files.exists(feature.resolve("dto/ListeningApiContract.java")));
    }
}
