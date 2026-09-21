package jp.co.translacat.domain.currency.service;

public class RateUnavailableException extends RuntimeException {
    public RateUnavailableException() {
        super("Exchange rate unavailable for the requested currency pair and date.");
    }
}
