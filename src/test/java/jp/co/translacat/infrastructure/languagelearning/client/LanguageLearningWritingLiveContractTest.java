package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardResponseDto;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientConfiguration;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 별도 실행한 Ktor·FastAPI·scratch MySQL에 BE 전송/DTO를 실제 연결한다.
 */
class LanguageLearningWritingLiveContractTest {
    @Test
    void realBeClientUsesKtorPythonAndDatabase() throws Exception {
        // 준비: 이 검사는 명시된 loopback 테스트 서버에만 접속한다.
        String url = System.getenv("LL_TEST_LIVE_URL");
        String secret = System.getenv("LL_TEST_JWT_SECRET_BASE64");
        assumeTrue(url != null && secret != null, "명시적 로컬 LL 테스트 서버가 필요합니다.");
        URI target = URI.create(url);
        assertEquals("http", target.getScheme());
        assertEquals("127.0.0.1", target.getHost());
        assertTrue(target.getPort() > 0);
        assertTrue(Base64.getDecoder().decode(secret).length >= 32);
        var properties = new LanguageLearningClientProperties();
        properties.setUrl(url);
        properties.getInternalJwt().setSecretBase64(secret);
        var config = new LanguageLearningClientConfiguration();
        var mapper = new ObjectMapper().findAndRegisterModules();
        var jwt = new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC());
        var writing = config.languageLearningWritingClient(RestClient.builder(), properties, jwt, mapper);
        var overview = new LanguageLearningOverviewClient(
                config.languageLearningRestClient(RestClient.builder(), properties), jwt, mapper);
        var settings = config.languageLearningSettingsClient(
                config.languageLearningRestClient(RestClient.builder(), properties), jwt, mapper);
        long userId = 1_000_000_000L + Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000_000L);
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Seoul"));
        settings.updateUserSettings(userId, new UserSettingUpdateRequestDto("ko", "en", "Asia/Seoul", 1));
        seedEligibleLearner(userId);

        // 실행: BE 클라이언트의 실제 POST와 GET을 LL 생성·모델 검증 경로에 보낸다.
        var created = writing.create(userId, DailyWritingType.FREE);
        assertEquals(DailySetStatus.GENERATING, created.status());
        var ready = waitFor(Duration.ofSeconds(30), () -> writing.get(userId, created.dailySetId()),
                value -> value.status() == DailySetStatus.READY);
        var duplicate = writing.create(userId, DailyWritingType.FREE);
        var foreign = assertThrows(LanguageLearningServiceException.class,
                () -> writing.get(userId + 1, created.dailySetId()));

        // 검증: 동일 snapshot은 한 세트이고 소유권과 실제 문항 DTO가 유지된다.
        assertEquals(created.dailySetId(), duplicate.dailySetId());
        assertEquals(1, ready.items().size());
        assertNotNull(ready.items().getFirst().contentRevision());
        // 원본 소유권 실패는 BusinessException의 400과 기능별 코드를 함께 보존한다.
        assertEquals(HttpStatus.BAD_REQUEST, foreign.getStatus());
        assertEquals("LANGUAGE_LEARNING_DAILY_SET_NOT_FOUND", foreign.getErrorCode());
        long itemId = ready.items().getFirst().itemId();

        // 실행: 새 LL 문항에 BE의 revision 보호 답변을 제출한다.
        var submitted = writing.submit(userId, itemId,
                new AnswerSubmitRequestDto("Synthetic answer", ready.items().getFirst().contentRevision()));
        var completed = waitFor(Duration.ofSeconds(30), () -> writing.get(userId, created.dailySetId()),
                value -> value.status() == DailySetStatus.COMPLETED);
        var duplicateAnswer = assertThrows(LanguageLearningServiceException.class,
                () -> writing.submit(userId, itemId,
                        new AnswerSubmitRequestDto("Synthetic answer", ready.items().getFirst().contentRevision())));

        // 검증: 평가 응답과 이력은 기존 BE DTO로 파싱되고 중복 답변은 거부된다.
        assertEquals(EvaluationStatus.PENDING, submitted.evaluationStatus());
        assertEquals(EvaluationStatus.SUCCESS, completed.items().getFirst().attempts().getFirst().evaluationStatus());
        assertEquals(created.dailySetId(), writing.history(userId, day, DailyWritingType.FREE).dailySetId());
        assertEquals(HttpStatus.BAD_REQUEST, duplicateAnswer.getStatus());
        assertEquals("LANGUAGE_LEARNING_ANSWER_NOT_ALLOWED", duplicateAnswer.getErrorCode());

        // 실행: 실제 외부 화면이 사용하는 공통 Overview 조회로 평가 집계를 확인한다.
        var dashboard = overview.get(userId, "/dashboard", Map.of("from", day, "to", day, "source", "WRITING"),
                DashboardResponseDto.class);
        var history = overview.list(userId, "/history", Map.of("source", "WRITING", "period", "all"),
                LearningHistoryItemResponseDto.class);

        // 검증: coverage는 평가 건수가 아닌 측정된 다섯 지표 수이며 표본·완료 건수는 각각 1이다.
        var performance = dashboard.activityPerformance().writing();
        var evaluation = completed.items().getFirst().attempts().getFirst().evaluation();
        var expectedMetrics = Map.of("MEANING", evaluation.meaning(), "GRAMMAR", evaluation.grammar(),
                "VOCABULARY", evaluation.vocabulary(), "NATURALNESS", evaluation.naturalness(),
                "EXPRESSION", evaluation.expression());
        assertEquals(1.0, performance.today().completed());
        assertEquals(5, performance.coverage().evaluated());
        assertEquals(5, performance.coverage().total());
        assertEquals(1, performance.sampleCount());
        assertEquals((double) evaluation.overall(), performance.recentScore());
        var trend = dashboard.trends().sourceMetrics();
        assertEquals(1, trend.sampleCount());
        assertEquals(expectedMetrics.keySet(), trend.metrics().keySet());
        expectedMetrics.forEach((metric, score) -> {
            var points = trend.metrics().get(metric);
            assertEquals(1, points.size());
            assertEquals(day, points.getFirst().date());
            assertEquals(score.doubleValue(), points.getFirst().score());
        });

        // 검증: 이력은 같은 공개 세트 식별자와 저장된 종합 점수로 한 건만 게시한다.
        assertEquals(1, history.size());
        assertEquals("WRITING:" + created.dailySetId(), history.getFirst().activityId());
        assertEquals((double) evaluation.overall(), history.getFirst().overallScore());
    }

    private static void seedEligibleLearner(long userId) throws Exception {
        // 준비: Writing 이전에 완료된 Level Test의 ACTIVE 결과만 scratch DB에 합성한다.
        String jdbcUrl = System.getenv("LL_TEST_MYSQL_URL");
        String username = System.getenv("LL_TEST_MYSQL_USERNAME");
        String password = System.getenv("LL_TEST_MYSQL_PASSWORD");
        assertNotNull(jdbcUrl);
        // 실행별 격리 MySQL 포트만 명시적으로 바꿀 수 있다. loopback/scratch catalog 제한은 유지한다.
        String configuredPort = System.getenv("LL_TEST_MYSQL_PORT");
        int testPort = configuredPort == null ? 33316 : Integer.parseInt(configuredPort);
        assertTrue(testPort > 0 && testPort <= 65535);
        assertTrue(jdbcUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:" + testPort
                + "/translacat_ll_it_live_[0-9a-f]{8}"));
        assertNotNull(username);
        assertNotNull(password);
        try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
             var statement = connection.prepareStatement("""
                     INSERT INTO language_learning_profile
                     (user_id,profile_version,state,base_level_score,evaluation_count,confidence,trend,additional_signals_json,
                      created_at,updated_at,created_by,updated_by)
                     VALUES (?,'PROFILE','ACTIVE',60,0,0.0,'stable','{}',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'TEST','TEST')
                     ON DUPLICATE KEY UPDATE state='ACTIVE',updated_at=UTC_TIMESTAMP(6)
                     """)) {
            statement.setLong(1, userId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static <T> T waitFor(Duration timeout, java.util.function.Supplier<T> query,
                                 java.util.function.Predicate<T> ready) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            T value = query.get();
            if (ready.test(value)) return value;
            Thread.sleep(100);
        }
        fail("로컬 LL Writing 처리가 기한 안에 완료되지 않았습니다.");
        return null;
    }
}
