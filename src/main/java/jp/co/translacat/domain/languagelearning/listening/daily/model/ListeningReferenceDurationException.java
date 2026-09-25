package jp.co.translacat.domain.languagelearning.listening.daily.model;

/**
 * Typed content-quality failure, separate from infrastructure retry.
 */
public final class ListeningReferenceDurationException extends RuntimeException {
    private final double measuredSeconds;
    private final String code;

    public ListeningReferenceDurationException(double measuredSeconds, String code) {
        super(code);
        this.measuredSeconds = measuredSeconds;
        this.code = code;
    }

    public double measuredSeconds() {
        return measuredSeconds;
    }

    public String code() {
        return code;
    }
}
