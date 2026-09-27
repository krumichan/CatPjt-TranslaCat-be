package jp.co.translacat.global.exception;

import jp.co.translacat.domain.languagelearning.keyword.controller.AdminLanguageLearningKeywordController;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.facade.LanguageLearningKeywordFacade;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LanguageLearningRequestExceptionAdviceTest {
    private static final String PATH = "/api/v1/admin/language-learning/system-keywords";

    @Test
    void invalidEnumReturnsSafe400BeforeFacadeCall() throws Exception {
        // 준비: 실제 관리자 Controller와 기존 전역 예외 처리기를 함께 연결한다.
        var facade = mock(LanguageLearningKeywordFacade.class);
        var mvc = mvc(facade);

        // 실행
        var result = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"synthetic-private-input\",\"type\":\"WORD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value(400))
                .andExpect(jsonPath("$.body.errorCode").value("LANGUAGE_LEARNING_REQUEST_INVALID"))
                .andExpect(jsonPath("$.body.path").value(PATH))
                .andReturn();

        // 검증: 제출 원문·Jackson 타입·예외 내용은 응답에 노출하지 않는다.
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("synthetic-private-input", "WORD", "Jackson", "Cannot deserialize");
        verifyNoInteractions(facade);
    }

    @Test
    void malformedJsonReturnsSameSafeContract() throws Exception {
        // 준비
        var facade = mock(LanguageLearningKeywordFacade.class);
        var mvc = mvc(facade);

        // 실행
        var result = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"synthetic-private-input\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.body.errorCode").value("LANGUAGE_LEARNING_REQUEST_INVALID"))
                .andReturn();

        // 검증
        assertThat(result.getResponse().getContentAsString()).doesNotContain("synthetic-private-input");
        verifyNoInteractions(facade);
    }

    @Test
    void validEnumStillReachesExistingFacade() throws Exception {
        // 준비
        var facade = mock(LanguageLearningKeywordFacade.class);
        var mvc = mvc(facade);

        // 실행
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Synthetic\",\"type\":\"VOCABULARY\"}"))
                .andExpect(status().isOk());

        // 검증: 기존 Controller의 사용자 식별자와 생성 흐름을 유지한다.
        verify(facade).createSystemKeyword(eq(900L), any(KeywordCreateRequestDto.class));
    }

    @Test
    void unrelatedControllerKeepsExistingExceptionContract() throws Exception {
        // 준비: 언어학습 패키지 밖의 Controller는 새 advice 범위에 포함하지 않는다.
        var mvc = mvc(mock(LanguageLearningKeywordFacade.class));

        // 실행 및 검증
        mvc.perform(post("/synthetic-unrelated").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WORD\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.body.errorCode").value(""));
    }

    private MockMvc mvc(LanguageLearningKeywordFacade facade) {
        var user = mock(User.class);
        when(user.getId()).thenReturn(900L);
        var principal = new UserPrincipal(user);
        return MockMvcBuilders.standaloneSetup(
                        new AdminLanguageLearningKeywordController(facade), new UnrelatedController())
                .setControllerAdvice(new ApiExceptionAdvice(), new LanguageLearningRequestExceptionAdvice())
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
                })
                .build();
    }

    @RestController
    static class UnrelatedController {
        @PostMapping("/synthetic-unrelated")
        void submit(@RequestBody KeywordCreateRequestDto ignored) {
        }
    }
}
