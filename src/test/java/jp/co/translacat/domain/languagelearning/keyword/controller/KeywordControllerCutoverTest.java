package jp.co.translacat.domain.languagelearning.keyword.controller;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.facade.LanguageLearningKeywordFacade;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class KeywordControllerCutoverTest {
    @Test
    void externalAdminPathAndSecurityAnnotationRemainStable() {
        var type = AdminLanguageLearningKeywordController.class;
        assertArrayEquals(new String[]{"/api/v1/admin/language-learning/system-keywords"},
                type.getAnnotation(RequestMapping.class).value());
        assertEquals("hasRole('ADMIN')", type.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void adminMethodsForwardAuthenticatedActorWithoutSyntheticRoleOrId() {
        var facade = mock(LanguageLearningKeywordFacade.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(900L);
        var principal = new UserPrincipal(user);
        var controller = new AdminLanguageLearningKeywordController(facade);
        var request = new KeywordCreateRequestDto("IT", KeywordType.TOPIC, null, null, null);
        controller.getSystemKeywords(principal);
        controller.createSystemKeyword(principal, request);
        verify(facade).getSystemKeywordsForAdmin(900L);
        verify(facade).createSystemKeyword(900L, request);
    }
}
