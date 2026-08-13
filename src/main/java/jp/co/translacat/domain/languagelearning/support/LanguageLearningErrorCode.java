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

    private LanguageLearningErrorCode() {
    }
}
