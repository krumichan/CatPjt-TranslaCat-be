package jp.co.translacat.domain.languagelearning.level.pool.support;

import java.util.List;

/**
 * Expected Level Test pool-generation rejection returned by the AI service.
 *
 * <p>This represents a generated candidate that failed content/diversity quality
 * checks, not an AI-server availability failure. The scheduler can skip the current
 * job and retry it on a later run without emitting an operational stack trace.</p>
 */
public class LevelTestPoolGenerationRejectedException extends RuntimeException {

    private final String code;
    private final List<String> reasons;

    public LevelTestPoolGenerationRejectedException(
            String code,
            List<String> reasons,
            Throwable cause
    ) {
        super(code, cause);
        this.code = code == null ? "UNKNOWN" : code;
        this.reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public String getCode() {
        return code;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
