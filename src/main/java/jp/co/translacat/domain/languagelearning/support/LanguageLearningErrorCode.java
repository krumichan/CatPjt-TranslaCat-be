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

    private LanguageLearningErrorCode() {
    }
}
