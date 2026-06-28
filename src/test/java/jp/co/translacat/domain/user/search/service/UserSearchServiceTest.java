package jp.co.translacat.domain.user.search.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.domain.user.search.dto.UserSearchResponseDto;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private UserSearchService userSearchService;

    @Test
    @DisplayName("publicId 검색 성공")
    void searchByPublicId() {
        // given
        Long loginUserId = 1L;
        User targetUser = createUser(2L, "target@example.com", "targetUser", "TCAT-00000002");
        UserSummaryProfileResponseDto profile = new UserSummaryProfileResponseDto(
                2L,
                "TCAT-00000002",
                "targetUser",
                null
        );

        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(targetUser));
        when(userProfileService.getSummaryByUser(targetUser)).thenReturn(profile);

        // when
        UserSearchResponseDto response = userSearchService.searchByPublicId(loginUserId, "TCAT-00000002");

        // then
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.publicId()).isEqualTo("TCAT-00000002");
        assertThat(response.nickname()).isEqualTo("targetUser");
        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.friendStatus()).isEqualTo(UserSearchFriendStatus.NONE);
    }

    @Test
    @DisplayName("존재하지 않는 publicId 처리")
    void failWhenPublicIdDoesNotExist() {
        // given
        Long loginUserId = 1L;

        when(userRepository.findByPublicId("UNKNOWN")).thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(
                () -> userSearchService.searchByPublicId(loginUserId, "UNKNOWN"),
                BusinessException.class
        );

        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo("PUBLIC_ID_NOT_FOUND");
    }

    @Test
    @DisplayName("자기 자신 검색 처리")
    void searchSelf() {
        // given
        Long loginUserId = 1L;
        User loginUser = createUser(1L, "me@example.com", "me", "TCAT-00000001");
        UserSummaryProfileResponseDto profile = new UserSummaryProfileResponseDto(
                1L,
                "TCAT-00000001",
                "me",
                null
        );

        when(userRepository.findByPublicId("TCAT-00000001")).thenReturn(Optional.of(loginUser));
        when(userProfileService.getSummaryByUser(loginUser)).thenReturn(profile);

        // when
        UserSearchResponseDto response = userSearchService.searchByPublicId(loginUserId, "TCAT-00000001");

        // then
        assertThat(response.friendStatus()).isEqualTo(UserSearchFriendStatus.SELF);
    }

    @Test
    @DisplayName("publicId가 비어 있으면 실패")
    void failWhenPublicIdIsBlank() {
        // given
        Long loginUserId = 1L;

        // when
        BusinessException exception = catchThrowableOfType(
                () -> userSearchService.searchByPublicId(loginUserId, "   "),
                BusinessException.class
        );

        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo("PUBLIC_ID_REQUIRED");
    }

    private User createUser(
            Long id,
            String email,
            String username,
            String publicId
    ) {
        User user = User.createLocalUser(
                email,
                "password",
                username,
                Role.USER,
                publicId
        );
        user.setId(id);
        return user;
    }
}
