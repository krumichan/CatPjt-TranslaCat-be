package jp.co.translacat.global.exception;

import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SettingsRemoteExceptionTest {
    private final ApiExceptionAdvice advice = new ApiExceptionAdvice();

    @Test
    void internalTokenFailureIsNotAFrontendLogin401() {
        var response = advice.handleLanguageLearningServiceException(
                new LanguageLearningServiceException(HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_REQUIRED", "실패"));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        var body = (ResponseDto<?>) response.getBody();
        assertNotNull(body);
        assertEquals(502, body.getResultCode());
        assertEquals("LL_INTERNAL_AUTH_FAILED", ((ErrorDto) body.getBody()).getErrorCode());
    }

    @Test
    void businessErrorsKeepExistingEnvelopeShape() {
        var response = advice.handleLanguageLearningServiceException(
                new LanguageLearningServiceException(HttpStatus.BAD_REQUEST, "SETTING_NOT_CONFIGURED", "설정 필요"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = (ResponseDto<?>) response.getBody();
        assertNotNull(body);
        assertEquals("SETTING_NOT_CONFIGURED", ((ErrorDto) body.getBody()).getErrorCode());
        assertEquals("Message <설정 필요>", body.getMessage());
    }

    @Test
    void disabledRemoteRemainsUnavailableRatherThanSuccessfulDefault() {
        var response = advice.handleLanguageLearningServiceException(
                new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE, "LL_SETTINGS_REMOTE_DISABLED",
                        "연결 비활성"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }
}
