package jp.co.translacat.domain.chat.room.dto.response;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectPartnerProfileResponseDtoTest {

    @Mock
    private User user;

    @Mock
    private UserProfile userProfile;

    @Mock
    private UserProfileImageUrlResolver imageUrlResolver;

    @Test
    void from_returnsLatestPartnerProfileFields() {
        when(user.getId()).thenReturn(2L);
        when(user.getPublicId()).thenReturn("TC-PFSN-CLNA");
        when(userProfile.getNickname()).thenReturn("상대 사용자");
        when(userProfile.getBio()).thenReturn("최신 상태 메시지");
        when(imageUrlResolver.resolveProfileImageUrl(userProfile))
                .thenReturn("https://cdn.example.com/profile.png");
        when(imageUrlResolver.resolveProfileBackgroundImageUrl(userProfile))
                .thenReturn("https://cdn.example.com/background.png");

        DirectPartnerProfileResponseDto result =
                DirectPartnerProfileResponseDto.from(
                        user,
                        userProfile,
                        imageUrlResolver
                );

        assertEquals(2L, result.userId());
        assertEquals("TC-PFSN-CLNA", result.publicId());
        assertEquals("상대 사용자", result.displayName());
        assertEquals(
                "https://cdn.example.com/profile.png",
                result.profileImageUrl()
        );
        assertEquals(
                "https://cdn.example.com/background.png",
                result.profileBackgroundImageUrl()
        );
        assertEquals("최신 상태 메시지", result.bio());

        verify(imageUrlResolver).resolveProfileImageUrl(userProfile);
        verify(imageUrlResolver)
                .resolveProfileBackgroundImageUrl(userProfile);
    }

    @Test
    void from_returnsFallbackDisplayNameAndNullProfileFieldsWhenProfileMissing() {
        when(user.getId()).thenReturn(2L);
        when(user.getPublicId()).thenReturn("TC-PFSN-CLNA");
        when(user.getUsername()).thenReturn("fallback-user");

        DirectPartnerProfileResponseDto result =
                DirectPartnerProfileResponseDto.from(
                        user,
                        null,
                        imageUrlResolver
                );

        assertEquals("fallback-user", result.displayName());
        assertNull(result.profileImageUrl());
        assertNull(result.profileBackgroundImageUrl());
        assertNull(result.bio());
        verifyNoInteractions(imageUrlResolver);
    }

    @Test
    void from_returnsNullWhenUserMissing() {
        DirectPartnerProfileResponseDto result =
                DirectPartnerProfileResponseDto.from(
                        null,
                        userProfile,
                        imageUrlResolver
                );

        assertNull(result);
        verifyNoInteractions(userProfile, imageUrlResolver);
    }
}
