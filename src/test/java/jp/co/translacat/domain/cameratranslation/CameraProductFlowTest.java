package jp.co.translacat.domain.cameratranslation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.SegmentResult;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.SegmentStatus;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.TranslationResult;
import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import jp.co.translacat.domain.user.deletion.AccountDeletionStore;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CameraProductFlowTest {
    private static final long OWNER = 1L;
    private static final long OTHER = 2L;
    private static final UUID INPUT_ID = UUID.fromString("30fb982d-ef25-484d-a3ca-86a48f4922e1");
    private static final UUID REQUEST_ID = UUID.fromString("7fa6a587-5bf4-4bf0-b041-3e03975dedfe");
    private static final String OCR_PATH = "/api/v1/image-translation/ocr-jobs";
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private JdbcTemplate jdbc;
    private CameraTranslationAi ai;
    private CameraOcrJobs jobs;
    private MockMvc http;

    /** 운영 SQL의 타입·정렬 지정만 H2에 맞춰 고립된 작업 DB를 준비한다. */
    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:camera_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table `user` (id bigint primary key, account_state varchar(32))");
        jdbc.update("insert into `user` values (1,'ACTIVE'),(2,'ACTIVE')");

        String ddl = Files.readString(Path.of("scripts/sql/20261122_camera_ocr_jobs.sql"))
                .replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
        jdbc.execute(ddl);

        var cipher = new CameraOcrCipher(Base64.getEncoder().encodeToString(new byte[32]));
        ai = mock(CameraTranslationAi.class);
        jobs = new CameraOcrJobs(new CameraOcrStore(jdbc), ai, cipher, new AccountDataLifecycleAccess(jdbc),
                json, new DataSourceTransactionManager(dataSource));
        http = MockMvcBuilders.standaloneSetup(new CameraTranslationController(new CameraTextTranslation(ai), jobs))
                .setControllerAdvice(new CameraTranslationErrors())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        authenticate(OWNER);
    }

    /** 각 테스트에서 설정한 합성 사용자를 제거한다. */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /** 고정 이미지에서 접수·중복·소유권·공통 결과·취소를 HTTP 계약으로 확인한다. */
    @Test
    void cropFlowsThroughOcrAndIsRemovedOnCancellation() throws Exception {
        // 준비
        byte[] pixels = png();
        UUID key = UUID.randomUUID();
        when(ai.recognize(eq(INPUT_ID), eq("ja"), any(), any())).thenReturn(sharedOcrExample());

        // 실행
        var accepted = http.perform(cropRequest(pixels, key, INPUT_ID))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.body.status").value("QUEUED"))
                .andReturn();
        String job = json.readTree(accepted.getResponse().getContentAsString()).path("body").path("jobId").asText();
        assertThat(accepted.getResponse().getHeader("Location")).isEqualTo(OCR_PATH + "/" + job);
        http.perform(cropRequest(pixels, key, INPUT_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.body.jobId").value(job));
        http.perform(cropRequest(pixels, key, UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.body.errorCode").value("IDEMPOTENCY_CONFLICT"));
        jobs.run();

        // 검증
        http.perform(get(OCR_PATH + "/" + job))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.result.imageId").value(INPUT_ID.toString()))
                .andExpect(jsonPath("$.body.result.regions[0].sourceText").value("帰ろう。"));
        authenticate(OTHER);
        http.perform(get(OCR_PATH + "/" + job)).andExpect(status().isNotFound());
        authenticate(OWNER);
        jdbc.update("update `user` set account_state='DELETION_PENDING' where id=?", OWNER);
        http.perform(get(OCR_PATH + "/" + job))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.body.errorCode").value("AUTH_REQUIRED"));
        jdbc.update("update `user` set account_state='ACTIVE' where id=?", OWNER);
        http.perform(delete(OCR_PATH + "/" + job)).andExpect(status().isNoContent());
        http.perform(delete(OCR_PATH + "/" + job)).andExpect(status().isNoContent());
        http.perform(get(OCR_PATH + "/" + job))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.status").value("CANCELED"))
                .andExpect(jsonPath("$.body.result").isEmpty());
        assertThat(jdbc.queryForObject(
                "select count(*) from camera_ocr_job where image_ciphertext is not null or result_ciphertext is not null",
                Integer.class)).isZero();
    }

    /** 모델 응답 순서가 달라도 표현 ID로 연결하고 누락된 표현의 오류를 반환한다. */
    @Test
    void translationPreservesClientOrderAndMarksMissingSegments() throws Exception {
        // 준비
        when(ai.translate(any(), any())).thenReturn(new TranslationResult(REQUEST_ID,
                List.of(new SegmentResult("second", SegmentStatus.TRANSLATED, "돌아가자", null))));
        String request = """
                {"requestId":"%s","mode":"STILL","sourceLanguage":"ja","targetLanguage":"ko",
                 "segments":[{"id":"first","text":"今日"},{"id":"second","text":"帰ろう"}]}
                """.formatted(REQUEST_ID);

        // 실행 및 검증
        http.perform(post("/api/v1/image-translation/translations").contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.segments[0].id").value("first"))
                .andExpect(jsonPath("$.body.segments[0].errorCode").value("INVALID_MODEL_OUTPUT"))
                .andExpect(jsonPath("$.body.segments[1].translatedText").value("돌아가자"));
    }

    /** 입력 귀속이 맞더라도 움직일 수 없는 행렬은 공통 OCR 결과로 허용하지 않는다. */
    @Test
    void refusesAnOcrResultWithNoninvertibleGeometry() throws Exception {
        // 준비
        JsonNode invalid = json.readTree(sharedOcrExample().toString().replace("[1,0,0,0,1,0]", "[0,0,0,0,0,0]"));

        // 실행 및 검증
        assertThatThrownBy(() -> CameraOcrResult.verify(INPUT_ID, invalid))
                .isInstanceOf(CameraTranslationProblem.class);
    }

    /** 공용 fixture가 제공된 실행에서 OCR v1의 세 상태를 실제 소비자와 대조한다. */
    @Test
    void consumesCanonicalSharedOcrExamples() throws Exception {
        String fixture = System.getenv("CAMERA_OCR_CONTRACT_EXAMPLES");
        org.junit.jupiter.api.Assumptions.assumeTrue(fixture != null, "Shared docs fixture is supplied by the runner");
        JsonNode examples = json.readTree(Files.readAllBytes(Path.of(fixture)));

        // 실행 및 검증: 복원 행렬·null 신뢰도·빈 결과가 같은 검증 경계를 통과한다.
        for (String field : List.of("recognition", "empty", "uncertain")) {
            var result = examples.path(field);
            CameraOcrResult.verify(UUID.fromString(result.path("imageId").asText()), result);
        }
    }

    /** 신규 SQL이 미적용인 경우 기존 계정 삭제는 카메라 단계에서 막히지 않는다. */
    @Test
    void enablesLegacyAccountDeletionWithoutTheCameraTable() {
        // 준비
        jdbc.execute("drop table camera_ocr_job");
        var operation = mock(AccountDeletionStore.Operation.class);
        when(operation.userId()).thenReturn(OWNER);

        // 실행 및 검증
        jobs.requireReady();
        assertThat(jobs.clean(operation)).isTrue();
    }

    /** 설치된 테이블의 컬럼 누락은 정상 미설치와 구분해 탈퇴 완료를 막는다. */
    @Test
    void refusesCleanupReadinessForAnIncompleteCameraSchema() {
        // 준비
        jdbc.execute("alter table camera_ocr_job drop column result_ciphertext");

        // 실행 및 검증
        assertThatThrownBy(jobs::requireReady).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    // 합성 사용자 ID 외의 인증 정보를 사용하지 않는다.
    private static void authenticate(long owner) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(owner);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(new UserPrincipal(user), ""));
    }

    // 공유 OCR v1의 문서 예제와 동일한 픽셀·영역 값에 클라이언트 ID만 대입한다.
    private JsonNode sharedOcrExample() throws Exception {
        return json.readTree("""
                {"schemaVersion":1,"imageId":"%s",
                 "image":{"widthPx":768,"heightPx":1440,"inputToImage":[1,0,0,0,1,0]},
                 "coordinateSpace":"image-pixels-top-left","status":"OK",
                 "regions":[{"segmentId":"balloon-0","readingOrder":0,
                   "polygon":[[108,270],[300,270],[300,388],[108,388]],
                   "sourceText":"帰ろう。","writingMode":"vertical-rtl","confidence":null}],
                 "engine":{"provider":"example","model":"example-ocr","version":"1"}}
                """.formatted(INPUT_ID));
    }

    // 사용자가 보낸 원본 파일을 사용하지 않고 정상 PNG를 만든다.
    private static byte[] png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(768, 1440, BufferedImage.TYPE_BYTE_GRAY), "png", output);
        return output.toByteArray();
    }

    // 실제 공개 multipart 요청에 필요한 값만 전달한다.
    private static MockMultipartHttpServletRequestBuilder cropRequest(byte[] png, UUID key, UUID imageId) {
        var request = multipart(OCR_PATH)
                .file(new MockMultipartFile("image", "fixture.png", "image/png", png));
        request.param("imageId", imageId.toString());
        request.param("sourceLanguage", "ja");
        request.header("Idempotency-Key", key.toString());
        return request;
    }
}
