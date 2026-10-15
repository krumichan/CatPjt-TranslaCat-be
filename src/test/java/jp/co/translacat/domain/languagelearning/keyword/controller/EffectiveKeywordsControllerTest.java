package jp.co.translacat.domain.languagelearning.keyword.controller;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.EffectiveKeywordsResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.facade.LanguageLearningKeywordFacade;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordCandidateSnapshot;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EffectiveKeywordsControllerTest {
    @Test
    void routeUsesAuthenticatedIdentityAndIgnoresSuppliedUserAndDate() throws Exception {
        // 준비: 외부 query 값으로 다른 사용자나 학습일을 지정해도 인증 사용자만 전달한다.
        var facade = mock(LanguageLearningKeywordFacade.class);
        var principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(123L);
        var candidate = new KeywordCandidateSnapshot(
                "CUSTOM:12", "요건정의", KeywordSource.CUSTOM, KeywordType.TOPIC, null, LocalDate.of(2026, 10, 15)
        );
        when(facade.getEffectiveKeywords(123L)).thenReturn(new EffectiveKeywordsResponseDto(List.of(candidate)));
        var mvc = MockMvcBuilders.standaloneSetup(new LanguageLearningKeywordController(facade))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        try {
            // 실행
            mvc.perform(get("/api/v1/language-learning/keywords/effective")
                            .param("userId", "999")
                            .param("learningDate", "2099-12-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body.candidates[0].key").value("CUSTOM:12"))
                    .andExpect(jsonPath("$.body.candidates[0].text").value("요건정의"));

            // 검증
            verify(facade).getEffectiveKeywords(123L);
            verifyNoMoreInteractions(facade);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void missingAuthenticatedPrincipalDoesNotQueryKeywords() {
        // 준비
        var facade = mock(LanguageLearningKeywordFacade.class);
        var controller = new LanguageLearningKeywordController(facade);

        // 실행 및 검증: Spring Security 외에도 기존 인증 사용자 확인을 거친다.
        assertThrows(BusinessException.class, () -> controller.getEffectiveKeywords(null));
        verifyNoInteractions(facade);
    }
}
