package jp.co.translacat.domain.accountbook.transaction.exception;

import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
public class ReceiptRegistrationException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    private ReceiptRegistrationException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public static ReceiptRegistrationException invalidKey() {
        return new ReceiptRegistrationException(
                "RECEIPT_IDEMPOTENCY_KEY_INVALID",
                HttpStatus.BAD_REQUEST,
                "A valid Idempotency-Key header is required.");
    }

    public static ReceiptRegistrationException idempotencyConflict() {
        return new ReceiptRegistrationException(
                "RECEIPT_IDEMPOTENCY_CONFLICT",
                HttpStatus.CONFLICT,
                "The idempotency key was already used with a different receipt batch.");
    }

    public static ReceiptRegistrationException quoteChanged(String receiptId) {
        return new ReceiptRegistrationException(
                "RECEIPT_QUOTE_CHANGED",
                HttpStatus.CONFLICT,
                "Receipt conversion changed and must be reviewed again: " + receiptId);
    }
}
