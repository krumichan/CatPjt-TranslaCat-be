package jp.co.translacat.domain.user.search.controller;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.search.dto.UserSearchResponseDto;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;
import jp.co.translacat.domain.user.search.service.UserSearchService;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserSearchControllerTest {

    private UserSearchService userSearchService;
    private UserSearchController userSearchController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userSearchService = mock(UserSearchService.class);
        userSearchController = new UserSearchController(userSearchService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(userSearchController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("publicId 사용자 검색 API 테스트")
    void searchByPublicId() throws Exception {
        // given
        Long loginUserId = 1L;
        setAuthentication(loginUserId);

        UserSearchResponseDto response = new UserSearchResponseDto(
                2L,
                "TCAT-00000002",
                "targetUser",
                null,
                UserSearchFriendStatus.NONE
        );

        when(userSearchService.searchByPublicId(loginUserId, "TCAT-00000002"))
                .thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/users/search")
                        .param("publicId", "TCAT-00000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.userId").value(2L))
                .andExpect(jsonPath("$.body.publicId").value("TCAT-00000002"))
                .andExpect(jsonPath("$.body.nickname").value("targetUser"))
                .andExpect(jsonPath("$.body.friendStatus").value("NONE"));

        verify(userSearchService).searchByPublicId(loginUserId, "TCAT-00000002");
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 publicId 사용자 검색을 할 수 없다")
    void failWhenUnauthenticated() {
        // when
        BusinessException exception = catchThrowableOfType(
                () -> userSearchController.searchByPublicId(null, "TCAT-00000002"),
                BusinessException.class
        );

        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED");
        verify(userSearchService, never()).searchByPublicId(anyLong(), anyString());
    }

    private void setAuthentication(Long userId) {
        User user = User.createLocalUser(
                "test@example.com",
                "password",
                "testUser",
                Role.USER,
                "TCAT-00000001"
        );
        user.setId(userId);

        UserPrincipal userPrincipal = new UserPrincipal(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userPrincipal,
                        null,
                        userPrincipal.getAuthorities()
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
