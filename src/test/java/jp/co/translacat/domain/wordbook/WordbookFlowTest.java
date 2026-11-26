package jp.co.translacat.domain.wordbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import jp.co.translacat.domain.user.deletion.AccountDeletionStore;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 계정·버전 경계에서 단어장 HTTP와 실제 JDBC 트랜잭션이 함께 작동하는지 확인한다. */
class WordbookFlowTest {
    private static final String PATH = "/api/v1/wordbook/entries";
    private static final String INITIAL = """
            {"sourceLanguage":"ja","targetLanguage":"ko","sourceText":"本日のおすすめ",
             "targetText":"오늘의 추천","sourceKind":"CAMERA_CAPTURE"}
            """;
    private static final String EDIT = """
            {"sourceLanguage":"ja","targetLanguage":"ko","sourceText":"本日のおすすめ",
             "targetText":"오늘 추천","context":null,"sourceLabel":"메뉴판"}
            """;
    private final ObjectMapper json = new ObjectMapper();
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    /** 독립 메모리 DB에 사용자 테이블과 제품 스키마를 준비하고 MVC 계약을 연결한다. */
    @BeforeEach
    void setUp() throws Exception {
        // H2에는 MySQL의 엔진·문자셋 지정만 빼고 동일 테이블과 제약을 적용한다.
        context = new AnnotationConfigApplicationContext();
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "jwt.token.secret-key=test-wordbook-only");
        context.register(Config.class);
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY, account_state VARCHAR(24) NOT NULL)");
        jdbc.update("INSERT INTO `user` (id, account_state) VALUES (1, 'ACTIVE'), (2, 'ACTIVE')");
        String sql = Files.readString(Path.of("scripts/sql/20261122_wordbook.sql"))
                .replace(" CHARACTER SET ascii COLLATE ascii_bin", "")
                .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin", "");
        var schema = new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8));
        var population = new ResourceDatabasePopulator(schema);
        population.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        population.execute(context.getBean(DriverManagerDataSource.class));

        // 인증 후 Spring MVC에서 사용하는 principal resolver와 제품 컨트롤러·advice를 사용한다.
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(WordbookController.class))
                .setControllerAdvice(context.getBean(WordbookAdvice.class))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        authenticate(1);
    }

    /**
     * 다음 테스트에 인증 정보와 메모리 DB 상태를 전달하지 않는다.
     *
     * @throws SQLException 테스트용 H2 데이터베이스 종료에 실패한 경우
     */
    @AfterEach
    void tearDown() throws SQLException {
        SecurityContextHolder.clearContext();

        try {
            if (jdbc != null) {
                // H2가 연결을 닫은 뒤 JdbcTemplate이 SQL 경고를 읽지 않도록 JDBC로 직접 종료한다.
                try (var connection = context.getBean(DriverManagerDataSource.class).getConnection();
                        var statement = connection.createStatement()) {
                    statement.execute("SHUTDOWN");
                }
            }
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    /** 만든 항목을 다시 읽고 편집한 뒤 삭제해도 이전 멱등 키가 항목을 되살리지 않는다. */
    @Test
    void preservesReplayAndVersionThroughEditAndDelete() throws Exception {
        // 최초 키와 본문을 보관한다. context 생략은 null로 저장된다.
        UUID firstKey = UUID.randomUUID();
        var created = create(firstKey, INITIAL)
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.body.entry.context").value(nullValue()));
        String id = entryId(created);

        // 초기 버전을 바꾸고, 이전 본문으로 전송한 생성 요청을 다시 읽는다.
        mvc.perform(put(PATH + "/" + id).header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.body.sourceKind").value("CAMERA_CAPTURE"));
        create(firstKey, INITIAL)
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.body.entry.targetText").value("오늘 추천"));
        create(firstKey, INITIAL.replace("오늘의 추천", "추천"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.body.errorCode").value("IDEMPOTENCY_CONFLICT"));

        // 조건 없는 쓰기와 오래된 쓰기를 거부한 뒤 삭제하고, 새로 만든 표현에 예전 키를 연결하지 않는다.
        mvc.perform(delete(PATH + "/" + id))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.body.errorCode").value("VERSION_REQUIRED"));
        mvc.perform(delete(PATH + "/" + id).header("If-Match", "\"1\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.body.errorCode").value("STALE_VERSION"));
        mvc.perform(delete(PATH + "/" + id).header("If-Match", "\"2\""))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        create(firstKey, INITIAL).andExpect(status().isNotFound());
        String recreated = entryId(create(UUID.randomUUID(), INITIAL).andExpect(status().isCreated()));
        assertThat(recreated).isNotEqualTo(id);
        create(firstKey, INITIAL).andExpect(status().isNotFound());
    }

    /** 표시 공백을 보존하고 정규화한 표현과 커서의 소유권을 확인한다. */
    @Test
    void isolatesAccountsAndReturnsExistingExpression() throws Exception {
        // 가시 원문의 내부 공백은 유지하고, 중복 판정은 유니코드 공백을 합친다.
        String first = INITIAL.replace("本日のおすすめ", "　本日　 のおすすめ　");
        String same = INITIAL.replace("本日のおすすめ", "本日 のおすすめ");
        String id = entryId(create(UUID.randomUUID(), first)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body.entry.sourceText").value("本日　 のおすすめ")));

        // 같은 계정에서 중복 요청과 편집 충돌은 이미 저장된 항목을 가리킨다.
        create(UUID.randomUUID(), same)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.duplicate").value(true))
                .andExpect(jsonPath("$.body.entry.id").value(id));
        String another = entryId(create(UUID.randomUUID(), INITIAL).andExpect(status().isCreated()));
        String conflicting = EDIT.replace("本日のおすすめ", "本日 のおすすめ").replace("오늘 추천", "오늘의 추천");
        mvc.perform(put(PATH + "/" + another).header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON).content(conflicting))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.body.errorCode").value("DUPLICATE_ENTRY"))
                .andExpect(jsonPath("$.body.existingEntryId").value(id));

        // 다음 페이지는 앱에서 고른 언어 필터와 같은 계정에서만 이어 읽는다.
        var page = mvc.perform(get(PATH).param("limit", "1").param("sourceLanguage", "ja")
                        .param("targetLanguage", "ko"))
                .andExpect(status().isOk())
                .andReturn();
        String cursor = json.readTree(page.getResponse().getContentAsByteArray()).path("body")
                .path("nextCursor").textValue();
        assertThat(cursor).isNotBlank();
        mvc.perform(get(PATH).param("limit", "1").param("cursor", cursor).param("sourceLanguage", "ja")
                        .param("targetLanguage", "ko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.nextCursor").value(nullValue()));
        mvc.perform(get(PATH).param("cursor", cursor)).andExpect(status().isBadRequest());

        authenticate(2);
        mvc.perform(get(PATH + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(get(PATH).param("cursor", cursor).param("sourceLanguage", "ja").param("targetLanguage", "ko"))
                .andExpect(status().isBadRequest());
        create(UUID.randomUUID(), first).andExpect(status().isCreated());
    }

    /** 접수된 계정 삭제는 저장·읽기를 막고 재전송 기록과 항목을 함께 제거한다. */
    @Test
    void cleansAcceptedDeletionAndRejectsLateRequests() throws Exception {
        // 계정 생명주기를 바꾸기 전에 양쪽 소유자에 단어장을 넣는다.
        create(UUID.randomUUID(), INITIAL).andExpect(status().isCreated());
        authenticate(2);
        create(UUID.randomUUID(), INITIAL).andExpect(status().isCreated());
        authenticate(1);

        // 기존 탈퇴 worker와 같은 SPI 계약으로 이미 접수된 계정을 정리한다.
        var operation = acceptDeletion();
        var cleanup = context.getBean(WordbookCleanup.class);
        cleanup.requireReady();
        assertThat(cleanup.clean(operation)).isTrue();
        assertThat(cleanup.clean(operation)).isTrue();

        // 인증 객체가 이전 상태로 남아 있어도 이후 요청은 DB의 활성 상태를 확인한다.
        create(UUID.randomUUID(), INITIAL).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForList("SELECT owner_id FROM wordbook_entry", Long.class)).containsExactly(2L);
        assertThat(jdbc.queryForList("SELECT owner_id FROM wordbook_create_request", Long.class)).containsExactly(2L);
    }

    /** 단어장 마이그레이션 전에는 기존 계정 삭제 및 정기 작업이 진행된다. */
    @Test
    void allowsAccountDeletionWithNeitherWordbookTableProvisioned() {
        // 계정 스키마는 둔 채 이번 테스트의 단어장 테이블만 제거한다.
        jdbc.execute("DROP TABLE wordbook_create_request");
        jdbc.execute("DROP TABLE wordbook_entry");
        var operation = acceptDeletion();
        var cleanup = context.getBean(WordbookCleanup.class);

        // 삭제 접수 확인과 후속 처리에서 각각 미설치를 유효한 빈 범위로 판단한다.
        cleanup.requireReady();
        assertThat(cleanup.clean(operation)).isTrue();
        cleanup.purgeExpiredReplays();
    }

    /** 한 테이블만 있는 상태를 정상 설치나 미설치로 판정하지 않는다. */
    @Test
    void rejectsAccountDeletionWhenWordbookIsPartiallyProvisioned() throws Exception {
        // 누락된 테이블 외에도 정리해야 할 요청 기록이 실제로 남아 있는 상태를 만든다.
        create(UUID.randomUUID(), INITIAL).andExpect(status().isCreated());
        jdbc.execute("DROP TABLE wordbook_entry");
        var operation = acceptDeletion();
        var cleanup = context.getBean(WordbookCleanup.class);

        // 부분 적용에서는 정리 준비와 완료 모두 실패해야 하며 기존 기록을 지우지 않는다.
        assertThatThrownBy(cleanup::requireReady).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cleanup.clean(operation)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wordbook_create_request", Long.class)).isEqualTo(1L);
    }

    /** 테이블이 있지만 정리 컬럼이 없을 때 실제 JDBC 오류를 전달한다. */
    @Test
    void rejectsMalformedWordbookCleanupSchema() {
        // 다른 SQLState를 내는 실제 잘못된 스키마 조건을 준비한다.
        jdbc.execute("ALTER TABLE wordbook_entry RENAME COLUMN owner_id TO wrong_owner_id");
        var operation = acceptDeletion();
        var cleanup = context.getBean(WordbookCleanup.class);

        // 오류를 단어장 미설치로 간주해 삭제 성공을 반환하지 않는다.
        assertThatThrownBy(cleanup::requireReady).isInstanceOf(BadSqlGrammarException.class);
        assertThatThrownBy(() -> cleanup.clean(operation)).isInstanceOf(BadSqlGrammarException.class);
    }

    /** 동시에 같은 표현을 등록해도 두 요청이 한 항목을 참조한다. */
    @Test
    void serializesConcurrentDuplicateRegistration() throws Exception {
        // 실제 JDBC 연결 두 개에서 같은 계정과 다른 멱등 키로 경쟁시킨다.
        var service = context.getBean(WordbookService.class);
        var draft = WordbookInput.draft("ja", "ko", "本日のおすすめ", "오늘의 추천", null, null);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Fixture start timed out");
                }
                return service.create(1, UUID.randomUUID(), draft, WordbookTypes.SourceKind.CAMERA_CAPTURE);
            });
            var second = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Fixture start timed out");
                }
                return service.create(1, UUID.randomUUID(), draft, WordbookTypes.SourceKind.SCREENSHOT_PICKER);
            });
            start.countDown();

            // 계정 잠금으로 한 요청만 신규 상태이며 두 멱등 기록은 서로 보존된다.
            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.entry().id()).isEqualTo(secondResult.entry().id());
            assertThat(List.of(firstResult.duplicate(), secondResult.duplicate()))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wordbook_create_request", Long.class)).isEqualTo(2L);
    }

    // 인증 자체는 기존 공용 보안 계층의 계약으로 처리되므로 MVC 테스트에는 확인된 principal을 공급한다.
    private void authenticate(long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(new UserPrincipal(user), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // 기존 탈퇴 접수 트랜잭션이 확정한 상태와 동일한 합성 계정을 준비한다.
    private AccountDeletionStore.Operation acceptDeletion() {
        jdbc.update("UPDATE `user` SET account_state='DELETION_PENDING' WHERE id=1");
        return new AccountDeletionStore.Operation(UUID.randomUUID().toString(), 1, null, "PENDING",
                Instant.now(), null, null, null);
    }

    // 호출자 키와 공개 JSON 형식을 그대로 MockMvc의 생성 경로에 전달한다.
    private ResultActions create(UUID key, String payload) throws Exception {
        return mvc.perform(post(PATH).header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    // 후속 수정·삭제 요청에서 사용할 저장된 항목 ID를 꺼낸다.
    private String entryId(ResultActions result) throws Exception {
        byte[] body = result.andReturn().getResponse().getContentAsByteArray();
        return json.readTree(body).path("body").path("entry").path("id").textValue();
    }

    /** 실제 트랜잭션 프록시를 사용하되 운영 설정과 외부 서비스가 없는 테스트 구성이다. */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({WordbookStore.class, WordbookCursor.class, AccountDataLifecycleAccess.class, WordbookService.class,
            WordbookController.class, WordbookAdvice.class, WordbookCleanup.class})
    static class Config {
        /** 테스트마다 고유한 메모리 DB에서 MySQL SQL에 호환되는 방언을 사용한다. */
        @Bean
        DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        /** 실제 저장소와 동일한 JDBC 호출 진입점을 제공한다. */
        @Bean
        JdbcTemplate jdbcTemplate(DriverManagerDataSource source) {
            return new JdbcTemplate(source);
        }

        /** 서비스와 계정 잠금이 같은 연결의 트랜잭션을 공유한다. */
        @Bean
        JdbcTransactionManager transactionManager(DriverManagerDataSource source) {
            return new JdbcTransactionManager(source);
        }
    }
}
