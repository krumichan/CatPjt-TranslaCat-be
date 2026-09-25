package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.model.ConfiguredLanguagePair;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningSettingsClient;
import jp.co.translacat.infrastructure.languagelearning.client.dto.ConfiguredLanguagePairsDto;
import jp.co.translacat.infrastructure.languagelearning.client.dto.LearningDateResponseDto;
import jp.co.translacat.infrastructure.languagelearning.client.dto.UserSettingsSnapshotDto;
import jp.co.translacat.support.SettingsSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

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
        var access = new RemoteSettingsAccess(provider, users);
        var error = assertThrows(LanguageLearningServiceException.class, () -> access.forUser(123L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        verifyNoInteractions(users, client);
    }

    @Test
    void missingCoreUserNeverCreatesRemoteLearner() {
        var gateway = new RemoteUserSettingsGateway(access());
        assertThrows(BusinessException.class, () -> gateway.getSnapshot(999L));
        verifyNoInteractions(client);
    }

    @Test
    void synchronizedSnapshotCarriesPostPromotionDateAndRevision() {
        var gateway = new RemoteUserSettingsGateway(access());
        var expected = SettingsSnapshotFixtures.user(123);
        when(client.getUserSnapshot(123L)).thenReturn(new UserSettingsSnapshotDto(123L,
                expected.learningDate(), expected.revision(), expected.settings()));
        assertEquals(expected, gateway.getSnapshot(123L));
        assertEquals(expected.learningDate(), gateway.resolveToday(expected));
        verify(client).getUserSnapshot(123L);
    }

    @Test
    void mismatchedUserResponseIsNotAccepted() {
        var gateway = new RemoteUserSettingsGateway(access());
        var snapshot = SettingsSnapshotFixtures.user(123);
        when(client.getUserSnapshot(123L)).thenReturn(new UserSettingsSnapshotDto(999L,
                snapshot.learningDate(), snapshot.revision(), snapshot.settings()));
        assertThrows(LanguageLearningServiceException.class, () -> gateway.getSnapshot(123L));
    }

    @Test
    void passiveDateDoesNotReadOrCreateUserSettings() {
        var gateway = new RemoteUserSettingsGateway(access());
        var date = LocalDate.of(2026, 9, 24);
        when(client.resolveLearningDate(999L)).thenReturn(new LearningDateResponseDto(date));
        assertEquals(date, gateway.resolveToday(999L));
        verifyNoInteractions(users);
        verify(client, never()).getUserSnapshot(any());
        verify(client, never()).getUserSettings(any());
    }

    @Test
    void remoteErrorDoesNotTryAnotherReadSource() {
        var gateway = new RemoteUserSettingsGateway(access());
        var failure = new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SERVICE_UNAVAILABLE", "실패");
        when(client.getUserSnapshot(123L)).thenThrow(failure);
        assertSame(failure, assertThrows(LanguageLearningServiceException.class, () -> gateway.getSnapshot(123L)));
        verify(client, times(1)).getUserSnapshot(123L);
        verifyNoMoreInteractions(client);
    }

    @Test
    void userUpdateUsesActualUserAndPreservesResponse() {
        var gateway = new RemoteUserSettingsGateway(access());
        var request = new UserSettingUpdateRequestDto(null, null, null, 7, null, null, null, null, null);
        var response = SettingsSnapshotFixtures.userDto();
        when(client.updateUserSettings(123L, request)).thenReturn(response);
        assertSame(response, gateway.update(123L, request));
    }

    @Test
    void backgroundAdminReadDoesNotInventAnAdminIdentity() {
        var gateway = new RemoteAdminSettingsGateway(access());
        when(client.getAdminPolicy()).thenReturn(SettingsSnapshotFixtures.admin().settings());
        assertEquals(1000, gateway.getSnapshot().resolvedLevelTestQuestionPoolTargetSize());
        verify(client).getAdminPolicy();
        verify(client, never()).getAdminSettings(any());
        verifyNoInteractions(users);
    }

    @Test
    void publicAdminReadUsesTheAuthenticatedCoreIdentity() {
        var gateway = new RemoteAdminSettingsGateway(access());
        when(client.getAdminSettings(123L)).thenReturn(SettingsSnapshotFixtures.admin().settings());
        gateway.getSettings(123L);
        verify(client).getAdminSettings(123L);
        verify(users).existsById(123L);
    }

    @Test
    void languagePairResultsAreImmutableAndReadThroughTheServiceContract() {
        var gateway = new RemoteUserSettingsGateway(access());
        var pair = new ConfiguredLanguagePair("ko", "ja");
        when(client.configuredLanguagePairs()).thenReturn(new ConfiguredLanguagePairsDto(List.of(pair)));
        var result = gateway.configuredLanguagePairs();
        assertEquals(List.of(pair), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add(pair));
    }
}
