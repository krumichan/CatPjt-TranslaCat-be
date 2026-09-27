package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningSettingsClient;
import jp.co.translacat.support.SettingsSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteSettingsGatewayTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<LanguageLearningSettingsClient> provider = mock(ObjectProvider.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LanguageLearningSettingsClient client = mock(LanguageLearningSettingsClient.class);

    private RemoteSettingsAccess access() {
        when(provider.getIfAvailable()).thenReturn(client);
        when(users.existsById(123L)).thenReturn(true);
        return new RemoteSettingsAccess(provider, users);
    }

    @Test
    void disabledClientFailsClosedWithoutCoreSettingsFallback() {
        // 준비
        var access = new RemoteSettingsAccess(provider, users);

        // 실행
        var error = assertThrows(LanguageLearningServiceException.class, () -> access.forUser(123L));

        // 검증
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        verifyNoInteractions(users, client);
    }

    @Test
    void missingCoreUserNeverCreatesRemoteLearner() {
        // 준비
        var gateway = new RemoteUserSettingsGateway(access());

        // 실행
        assertThrows(BusinessException.class, () -> gateway.get(999L));

        // 검증
        verifyNoInteractions(client);
    }

    @Test
    void userReadReturnsTheRemotePublicContract() {
        // 준비
        var gateway = new RemoteUserSettingsGateway(access());
        var response = SettingsSnapshotFixtures.userDto();
        when(client.getUserSettings(123L)).thenReturn(response);

        // 실행
        var actual = gateway.get(123L);

        // 검증
        assertSame(response, actual);
        verify(client).getUserSettings(123L);
        verifyNoMoreInteractions(client);
    }

    @Test
    void remoteErrorDoesNotTryAnotherReadSource() {
        // 준비
        var gateway = new RemoteUserSettingsGateway(access());
        var failure = new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SERVICE_UNAVAILABLE", "실패");
        when(client.getUserSettings(123L)).thenThrow(failure);

        // 실행
        var actual = assertThrows(LanguageLearningServiceException.class, () -> gateway.get(123L));

        // 검증
        assertSame(failure, actual);
        verify(client, times(1)).getUserSettings(123L);
        verifyNoMoreInteractions(client);
    }

    @Test
    void userUpdateUsesActualUserAndPreservesResponse() {
        // 준비
        var gateway = new RemoteUserSettingsGateway(access());
        var request = new UserSettingUpdateRequestDto(null, null, null, 7, null, null, null, null, null);
        var response = SettingsSnapshotFixtures.userDto();
        when(client.updateUserSettings(123L, request)).thenReturn(response);

        // 실행
        var actual = gateway.update(123L, request);

        // 검증
        assertSame(response, actual);
    }

    @Test
    void publicAdminReadUsesTheAuthenticatedCoreIdentity() {
        // 준비
        var gateway = new RemoteAdminSettingsGateway(access());
        var response = SettingsSnapshotFixtures.adminDto();
        when(client.getAdminSettings(123L)).thenReturn(response);

        // 실행
        var actual = gateway.getSettings(123L);

        // 검증
        assertSame(response, actual);
        verify(client).getAdminSettings(123L);
        verify(users).existsById(123L);
    }
}
