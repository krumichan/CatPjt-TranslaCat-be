package jp.co.translacat.domain.languagelearning.history.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.dashboard.port.OverviewGateway;
import jp.co.translacat.domain.languagelearning.history.service.LearningHistoryQueryService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.ApiExceptionAdvice;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.LinkedHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearningEvidenceControllerTest {
    private final OverviewGateway gateway = mock(OverviewGateway.class);

    @Test
    void 실제_인증_소유자와_모든_조회_조건을_LL로_전달한다() throws Exception {
        // 준비
        var expected = new LinkedHashMap<String, Object>();
        expected.put("source", "SPEAKING");
        expected.put("learningLanguage", "en");
        expected.put("from", "2026-09-01");
        expected.put("to", "2026-10-03");
        expected.put("resultKind", "SESSION_COACHING");
        expected.put("policyVersion", "free-session-coaching-v1");
        expected.put("cursor", "old:cursor");
        expected.put("limit", "10");
        when(gateway.get(eq(101L), eq("/evidence"), eq(expected), eq(JsonNode.class)))
                .thenReturn(new ObjectMapper().readTree("{\"items\":[],\"nextCursor\":null}"));

        // 실행
        mvc(101L).perform(get("/api/v1/language-learning/history/evidence")
                        .param("source", "SPEAKING").param("learningLanguage", "en")
                        .param("from", "2026-09-01").param("to", "2026-10-03")
                        .param("resultKind", "SESSION_COACHING").param("policyVersion", "free-session-coaching-v1")
                        .param("cursor", "old:cursor").param("limit", "10").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body.items").isEmpty());

        // 검증: 요청의 임의 userId는 소유자를 바꾸지 않는다.
        verify(gateway).get(101L, "/evidence", expected, JsonNode.class);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void LL_장애를_빈_근거로_바꾸지_않는다() throws Exception {
        // 준비
        when(gateway.get(eq(101L), eq("/evidence"), anyMap(), eq(JsonNode.class)))
                .thenThrow(new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                        "LL_OVERVIEW_UNAVAILABLE", "학습 조회 서비스에 연결하지 못했습니다."));

        // 실행
        mvc(101L).perform(get("/api/v1/language-learning/history/evidence"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.body.errorCode").value("LL_OVERVIEW_UNAVAILABLE"));

        // 검증
        verify(gateway).get(eq(101L), eq("/evidence"), anyMap(), eq(JsonNode.class));
    }

    @Test
    void 인증이_없으면_근거_조회에_도달하지_않는다() throws Exception {
        // 준비
        var mvc = mvc(null);

        // 실행
        mvc.perform(get("/api/v1/language-learning/history/evidence"))
                .andExpect(jsonPath("$.body.errorCode").value("UNAUTHORIZED"));

        // 검증
        verifyNoInteractions(gateway);
    }

    private MockMvc mvc(Long userId) {
        var user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        var principal = userId == null ? null : new UserPrincipal(user);
        return MockMvcBuilders.standaloneSetup(new LearningHistoryController(new LearningHistoryQueryService(gateway)))
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
