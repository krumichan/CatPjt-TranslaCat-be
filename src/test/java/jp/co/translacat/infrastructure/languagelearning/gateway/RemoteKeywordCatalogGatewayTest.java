package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordListResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordLearningFacts;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningKeywordClient;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteKeywordCatalogGatewayTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<LanguageLearningKeywordClient> clients = mock(ObjectProvider.class);
    private final LanguageLearningKeywordClient client = mock(LanguageLearningKeywordClient.class);
    private final UserRepository users = mock(UserRepository.class);
    private final KeywordLearningFacts facts = mock(KeywordLearningFacts.class);
    private final RemoteKeywordCatalogGateway gateway = new RemoteKeywordCatalogGateway(clients, users, facts);

    @BeforeEach
    void setup() {
        when(clients.getIfAvailable()).thenReturn(client);
        when(users.existsById(123L)).thenReturn(true);
        when(users.existsById(900L)).thenReturn(true);
    }

    @Test
    void disabledRemoteNeverFallsBackToCoreCatalog() {
        when(clients.getIfAvailable()).thenReturn(null);
        var error = assertThrows(LanguageLearningServiceException.class, () -> gateway.getKeywords(123L, "ko"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("LL_KEYWORDS_REMOTE_DISABLED", error.getErrorCode());
        verifyNoInteractions(client, users, facts);
    }

    @Test
    void unknownUserDoesNotGetAnInternalTokenOrRemoteWrite() {
        var change = new KeywordCreateRequestDto("IT", KeywordType.TOPIC, null, null, null);
        assertThrows(BusinessException.class, () -> gateway.createCustom(999L, change));
        verifyNoInteractions(client, facts);
    }

    @Test
    void coreLearningFactAndLocaleArePassedWithoutFabricatedUser() {
        when(facts.hasStartedLearning(123L)).thenReturn(true);
        var response = new KeywordListResponseDto(List.of(), List.of());
        when(client.list(123L, true, "learning")).thenReturn(response);
        assertSame(response, gateway.getKeywords(123L, "learning"));
        verify(client).list(123L, true, "learning");
    }

    @Test
    void creationAndSelectionUseCurrentCoreFact() {
        var change = new KeywordCreateRequestDto("IT", KeywordType.TOPIC, null, null, null);
        gateway.createCustom(123L, change);
        when(facts.hasStartedLearning(123L)).thenReturn(true);
        gateway.selectSystem(123L, 10L, true);
        verify(client).createCustom(123L, false, change);
        verify(client).selectSystem(123L, true, 10L, true);
    }

    @Test
    void administratorIdentityIsNotReplacedByAnArbitraryId() {
        when(client.listSystem(900L)).thenReturn(List.of());
        assertTrue(gateway.getSystemKeywords(900L).isEmpty());
        verify(client).listSystem(900L);
        verifyNoInteractions(facts);
    }

    @Test
    void candidateDateAndRemoteFailureArePreservedWithoutRetry() {
        var date = LocalDate.of(2026, 9, 24);
        var error = new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "TEST_DOWN", "테스트 장애");
        when(client.candidates(123L, false, date)).thenThrow(error);
        assertSame(error, assertThrows(LanguageLearningServiceException.class, () -> gateway.candidates(123L, date)));
        verify(client, times(1)).candidates(123L, false, date);
    }
}
