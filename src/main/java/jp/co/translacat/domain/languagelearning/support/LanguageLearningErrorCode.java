package jp.co.translacat.domain.languagelearning.support;

public final class LanguageLearningErrorCode {

    public static final String SETTING_INVALID =
            "LANGUAGE_LEARNING_SETTING_INVALID";
    public static final String SETTING_NOT_CONFIGURED =
            "LANGUAGE_LEARNING_SETTING_NOT_CONFIGURED";
    public static final String USER_NOT_FOUND =
            "LANGUAGE_LEARNING_USER_NOT_FOUND";

    public static final String KEYWORD_DUPLICATED =
            "LANGUAGE_LEARNING_KEYWORD_DUPLICATED";
    public static final String KEYWORD_NOT_FOUND =
            "LANGUAGE_LEARNING_KEYWORD_NOT_FOUND";
    public static final String KEYWORD_ACCESS_DENIED =
            "LANGUAGE_LEARNING_KEYWORD_ACCESS_DENIED";
    public static final String KEYWORD_HIERARCHY_INVALID =
            "LANGUAGE_LEARNING_KEYWORD_HIERARCHY_INVALID";

    public static final String LEVEL_TEST_REQUIRED =
            "LANGUAGE_LEARNING_LEVEL_TEST_REQUIRED";
    public static final String LEVEL_TEST_NOT_FOUND =
            "LANGUAGE_LEARNING_LEVEL_TEST_NOT_FOUND";
    public static final String LEVEL_TEST_INVALID_STATE =
            "LANGUAGE_LEARNING_LEVEL_TEST_INVALID_STATE";

    public static final String DAILY_SET_NOT_FOUND =
            "LANGUAGE_LEARNING_DAILY_SET_NOT_FOUND";
    public static final String DAILY_SET_GENERATING =
            "LANGUAGE_LEARNING_DAILY_SET_GENERATING";
    public static final String DAILY_SET_GENERATION_FAILED =
            "LANGUAGE_LEARNING_DAILY_SET_GENERATION_FAILED";
    public static final String DAILY_ITEM_NOT_FOUND =
            "LANGUAGE_LEARNING_DAILY_ITEM_NOT_FOUND";
    public static final String REGENERATION_LIMIT =
            "LANGUAGE_LEARNING_REGENERATION_LIMIT";
    public static final String WRITING_REGENERATION_IN_PROGRESS =
            "LANGUAGE_LEARNING_WRITING_REGENERATION_IN_PROGRESS";
    public static final String WRITING_REGENERATION_CONFLICT =
            "LANGUAGE_LEARNING_WRITING_REGENERATION_CONFLICT";
    public static final String WRITING_ITEM_STALE =
            "LANGUAGE_LEARNING_WRITING_ITEM_STALE";
    public static final String ANSWER_NOT_ALLOWED =
            "LANGUAGE_LEARNING_ANSWER_NOT_ALLOWED";
    public static final String REVIEW_EXPIRED =
            "LANGUAGE_LEARNING_REVIEW_EXPIRED";

    public static final String EVALUATION_FAILED =
            "LANGUAGE_LEARNING_EVALUATION_FAILED";
    public static final String JSON_PROCESSING_FAILED =
            "LANGUAGE_LEARNING_JSON_PROCESSING_FAILED";

    public static final String SPEAKING_DISABLED =
            "SPEAKING_DISABLED";
    public static final String DAILY_LIMIT_EXCEEDED =
            "DAILY_LIMIT_EXCEEDED";
    public static final String SPEAKING_TOPIC_NOT_FOUND =
            "SPEAKING_TOPIC_NOT_FOUND";
    public static final String SESSION_NOT_FOUND =
            "SESSION_NOT_FOUND";
    public static final String SESSION_NOT_ACTIVE =
            "SESSION_NOT_ACTIVE";
    public static final String TURN_ALREADY_EXISTS =
            "TURN_ALREADY_EXISTS";
    public static final String TURN_NOT_FOUND =
            "TURN_NOT_FOUND";
    public static final String INVALID_TURN_ORDER =
            "INVALID_TURN_ORDER";
    public static final String INVALID_AUDIO =
            "INVALID_AUDIO";
    public static final String AUDIO_UPLOAD_EXPIRED =
            "AUDIO_UPLOAD_EXPIRED";
    public static final String TURN_PROCESSING =
            "TURN_PROCESSING";
    public static final String STT_FAILED =
            "STT_FAILED";
    public static final String TTS_FAILED =
            "TTS_FAILED";
    public static final String EVALUATION_PENDING =
            "EVALUATION_PENDING";
    public static final String SPEAKING_EVALUATION_FAILED =
            "EVALUATION_FAILED";
    public static final String INSUFFICIENT_EVIDENCE =
            "INSUFFICIENT_EVIDENCE";
    public static final String STT_REPORT_NOT_FOUND =
            "STT_REPORT_NOT_FOUND";
    public static final String SPEAKING_ASSISTANCE_FAILED =
            "SPEAKING_ASSISTANCE_FAILED";
    public static final String SPEAKING_EVALUATION_SKIP_NOT_ALLOWED =
            "SPEAKING_EVALUATION_SKIP_NOT_ALLOWED";
    public static final String FORBIDDEN =
            "FORBIDDEN";
    public static final String DASHBOARD_SOURCE_INVALID =
            "LANGUAGE_LEARNING_DASHBOARD_SOURCE_INVALID";

    public static final String LISTENING_SETTING_REQUIRED =
            "LISTENING_SETTING_REQUIRED";
    public static final String LISTENING_DAILY_LIMIT_EXCEEDED =
            "LISTENING_DAILY_LIMIT_EXCEEDED";
    public static final String LISTENING_INVALID_TASK_COMBINATION =
            "LISTENING_INVALID_TASK_COMBINATION";
    public static final String LISTENING_ACTIVE_SESSION_EXISTS =
            "LISTENING_ACTIVE_SESSION_EXISTS";
    public static final String LISTENING_SESSION_EXPIRED =
            "LISTENING_SESSION_EXPIRED";
    public static final String LISTENING_INVALID_STATE =
            "LISTENING_INVALID_STATE";
    public static final String LISTENING_IDEMPOTENCY_CONFLICT =
            "LISTENING_IDEMPOTENCY_CONFLICT";
    public static final String LISTENING_ITEM_ALREADY_SUBMITTED =
            "LISTENING_ITEM_ALREADY_SUBMITTED";
    public static final String LISTENING_AUDIO_INVALID =
            "LISTENING_AUDIO_INVALID";
    public static final String LISTENING_RERECORD_LIMIT_EXCEEDED =
            "LISTENING_RERECORD_LIMIT_EXCEEDED";
    public static final String LISTENING_ANSWER_REVEALED =
            "LISTENING_ANSWER_REVEALED";
    public static final String LISTENING_PRACTICE_LIMIT_EXCEEDED =
            "LISTENING_PRACTICE_LIMIT_EXCEEDED";
    public static final String LISTENING_REPORT_AUDIO_EXPIRED =
            "LISTENING_REPORT_AUDIO_EXPIRED";
    public static final String LISTENING_REPORT_ALREADY_SUBMITTED =
            "LISTENING_REPORT_ALREADY_SUBMITTED";
    public static final String LISTENING_REPLACEMENT_LIMIT_EXCEEDED =
            "LISTENING_REPLACEMENT_LIMIT_EXCEEDED";
    public static final String LISTENING_PLAYBACK_EVENT_INVALID =
            "LISTENING_PLAYBACK_EVENT_INVALID";
    public static final String LEVEL_TEST_DAILY_LIMIT_REACHED =
            "LEVEL_TEST_DAILY_LIMIT_REACHED";
    public static final String LEVEL_TEST_ANSWER_MODE_MISMATCH =
            "LEVEL_TEST_ANSWER_MODE_MISMATCH";
    public static final String LEVEL_TEST_AUDIO_INVALID =
            "LEVEL_TEST_AUDIO_INVALID";
    public static final String LEVEL_TEST_EVALUATION_FAILED =
            "LEVEL_TEST_EVALUATION_FAILED";
    public static final String CONTENT_DIVERSITY_EXHAUSTED =
            "CONTENT_DIVERSITY_EXHAUSTED";
    public static final String AI_GENERATION_FAILED =
            "AI_GENERATION_FAILED";
    public static final String AI_TTS_FAILED =
            "AI_TTS_FAILED";
    public static final String AI_STT_FAILED =
            "AI_STT_FAILED";
    public static final String AI_EVALUATION_FAILED =
            "AI_EVALUATION_FAILED";
    public static final String AI_EXPLANATION_FAILED =
            "AI_EXPLANATION_FAILED";
    public static final String AI_SCHEMA_INVALID =
            "AI_SCHEMA_INVALID";

    private LanguageLearningErrorCode() {
    }
}
