package jp.co.translacat.domain.languagelearning.daily.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.ApiExceptionAdvice;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningWritingClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WritingPreparationControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final LanguageLearningWritingClient client = mock(LanguageLearningWritingClient.class);

    @Test
    void preparationUsesAuthenticatedPrincipalAndOriginalRequestBody() throws Exception {
        // 준비: 임의 userId 쿼리는 인증 소유자를 대신할 수 없다.
        var body = json.readTree("{\"requestKey\":\"prep-1\",\"writingType\":\"GUIDED\",\"targetBand\":3,\"topicKey\":\"school-life\"}");
        when(client.writingPreparationStart(101L, body)).thenReturn(json.readTree("{\"requestKey\":\"prep-1\"}"));

        // 실행
        mvc(101L, true).perform(post("/api/v1/language-learning/writing/curated/preparations")
                        .param("userId", "999").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body.requestKey").value("prep-1"));

        // 검증
        verify(client).writingPreparationStart(101L, body);
        verifyNoMoreInteractions(client);
    }

    @Test
    void getAndReplacementUseOwnerAndDoNotInvokePreparationStart() throws Exception {
        // 준비
        when(client.writingPreparationGet(101L, "prep-1")).thenReturn(json.readTree("{\"status\":\"UNCERTAIN\"}"));
        var body = json.readTree("{\"requestKey\":\"repair-1\",\"contentRevision\":\"revision-1\",\"planRevision\":2}");
        when(client.writingPlanReplacement(101L, -99L, body)).thenReturn(json.readTree("{\"planRevision\":3}"));
        var mvc = mvc(101L, true);

        // 실행
        mvc.perform(get("/api/v1/language-learning/writing/curated/preparations/prep-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body.status").value("UNCERTAIN"));
        mvc.perform(post("/api/v1/language-learning/writing/curated/plans/items/-99/replacement")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body.planRevision").value(3));

        // 검증
        verify(client).writingPreparationGet(101L, "prep-1");
        verify(client).writingPlanReplacement(101L, -99L, body);
        verifyNoMoreInteractions(client);
    }

    @Test
    void missingAuthenticationDoesNotReachAnyWritingOperation() throws Exception {
        // 준비
        var mvc = mvc(null, true);

        // 실행
        mvc.perform(get("/api/v1/language-learning/writing/curated/preparations/prep-1"))
                .andExpect(jsonPath("$.body.errorCode").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/language-learning/writing/curated/preparations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.body.errorCode").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/language-learning/writing/curated/plans/items/-99/replacement")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.body.errorCode").value("UNAUTHORIZED"));

        // 검증
        verifyNoInteractions(client);
    }

    @Test
    void keepsNotFoundConflictAndUnavailableAsFailures() throws Exception {
        // 준비
        when(client.writingPreparationGet(101L, "foreign")).thenThrow(new LanguageLearningServiceException(
                HttpStatus.NOT_FOUND, "WRITING_PREPARATION_NOT_FOUND", "not found"));
        when(client.writingPlanReplacement(eq(101L), eq(-99L), any())).thenThrow(new LanguageLearningServiceException(
                HttpStatus.CONFLICT, "WRITING_PREPARATION_CONFLICT", "conflict"));
        var mvc = mvc(101L, true);

        // 실행·검증: 실패를 빈 성공으로 바꾸거나 새 준비 명령을 실행하지 않는다.
        mvc.perform(get("/api/v1/language-learning/writing/curated/preparations/foreign"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.body.errorCode").value("WRITING_PREPARATION_NOT_FOUND"));
        mvc.perform(post("/api/v1/language-learning/writing/curated/plans/items/-99/replacement")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.body.errorCode").value("WRITING_PREPARATION_CONFLICT"));
        mvc(101L, false).perform(get("/api/v1/language-learning/writing/curated/preparations/prep-1"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.body.errorCode").value("LL_WRITING_REMOTE_DISABLED"));
        verify(client, never()).writingPreparationStart(anyLong(), any());
    }

    @Test
    void preparationAndReplacementControllersKeepExplicitQaGate() {
        // 준비: 프로퍼티를 생략하거나 false로 두면 두 경로 자체가 등록되지 않는다.
        var context = new ApplicationContextRunner()
                .withUserConfiguration(WritingPreparationController.class, WritingPlanController.class)
                .withBean(LanguageLearningWritingClient.class, () -> client);

        // 실행·검증
        context.run(value -> {
            org.assertj.core.api.Assertions.assertThat(value).doesNotHaveBean(WritingPreparationController.class);
            org.assertj.core.api.Assertions.assertThat(value).doesNotHaveBean(WritingPlanController.class);
        });
        context.withPropertyValues("app.language-learning.writing-paragraph-qa-enabled=false").run(value ->
                org.assertj.core.api.Assertions.assertThat(value).doesNotHaveBean(WritingPreparationController.class));
        context.withPropertyValues("app.language-learning.writing-paragraph-qa-enabled=true").run(value -> {
            org.assertj.core.api.Assertions.assertThat(value).hasSingleBean(WritingPreparationController.class);
            org.assertj.core.api.Assertions.assertThat(value).hasSingleBean(WritingPlanController.class);
        });
    }

    private MockMvc mvc(Long userId, boolean connected) {
        var user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        var principal = userId == null ? null : new UserPrincipal(user);
        var beans = new StaticListableBeanFactory();
        if (connected) beans.addBean("client", client);
        var provider = beans.getBeanProvider(LanguageLearningWritingClient.class);
        return MockMvcBuilders.standaloneSetup(new WritingPreparationController(provider), new WritingPlanController(provider))
                .setControllerAdvice(new ApiExceptionAdvice())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory factory) {
                        return principal;
                    }
                }).build();
    }
}
