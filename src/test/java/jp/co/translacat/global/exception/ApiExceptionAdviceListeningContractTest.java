package jp.co.translacat.global.exception;

import jp.co.translacat.domain.languagelearning.listening.support.ListeningErrorDto;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.dto.ResponseDto;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionAdviceListeningContractTest {

    private final ApiExceptionAdvice advice = new ApiExceptionAdvice();

    @Test
    void idempotencyConflictUsesStructuredListeningContractAnd409() {
        BusinessException exception = new BusinessException(
                "same key with different payload",
                LanguageLearningErrorCode.LISTENING_IDEMPOTENCY_CONFLICT
        );

        ResponseEntity<?> entity = advice.handleBusinessException(exception);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(entity.getBody()).isInstanceOf(ResponseDto.class);
        ResponseDto<?> response = (ResponseDto<?>) entity.getBody();
        assertThat(response.getResultCode()).isEqualTo(409);
        assertThat(response.getBody()).isInstanceOf(ListeningErrorDto.class);

        ListeningErrorDto error = (ListeningErrorDto) response.getBody();
        assertThat(error.code())
                .isEqualTo(LanguageLearningErrorCode.LISTENING_IDEMPOTENCY_CONFLICT);
        assertThat(error.messageKey())
                .isEqualTo("languageLearning.listening.listening_idempotency_conflict");
        assertThat(error.retryable()).isFalse();
        assertThat(error.retryAfterSeconds()).isNull();
        assertThat(error.failedStage()).isNull();
        assertThat(error.resourceId()).isNull();
    }

    @Test
    void listeningValidationErrorUsesSameStructuredContractAnd400() {
        BusinessException exception = new BusinessException(
                "invalid task combination",
                LanguageLearningErrorCode.LISTENING_INVALID_TASK_COMBINATION
        );

        ResponseEntity<?> entity = advice.handleBusinessException(exception);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseDto<?> response = (ResponseDto<?>) entity.getBody();
        assertThat(response.getBody()).isInstanceOf(ListeningErrorDto.class);
        ListeningErrorDto error = (ListeningErrorDto) response.getBody();
        assertThat(error.code())
                .isEqualTo(LanguageLearningErrorCode.LISTENING_INVALID_TASK_COMBINATION);
    }
}
