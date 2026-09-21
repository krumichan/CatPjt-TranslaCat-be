package jp.co.translacat.domain.currency.service;

/** A transient transport, throttling, or upstream server failure; no response body is retained. */
public final class RetryableRateUnavailableException extends RateUnavailableException {}
