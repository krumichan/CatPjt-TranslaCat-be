package jp.co.translacat.domain.chat.ai.support;

public final class ChatAiErrorCode {

    public static final String ROOM_NOT_FOUND = "CHAT_AI_ROOM_NOT_FOUND";
    public static final String ROOM_TYPE_NOT_SUPPORTED = "CHAT_AI_ROOM_TYPE_NOT_SUPPORTED";
    public static final String ROOM_MEMBER_ACCESS_DENIED = "CHAT_AI_ROOM_MEMBER_ACCESS_DENIED";
    public static final String ROOM_MANAGEMENT_ACCESS_DENIED = "CHAT_AI_ROOM_MANAGEMENT_ACCESS_DENIED";
    public static final String MEMBER_NOT_FOUND = "CHAT_AI_MEMBER_NOT_FOUND";
    public static final String REQUEST_REQUIRED = "CHAT_AI_REQUEST_REQUIRED";
    public static final String MAX_MEMBER_COUNT_EXCEEDED = "CHAT_AI_MAX_MEMBER_COUNT_EXCEEDED";
    public static final String NICKNAME_REQUIRED = "CHAT_AI_NICKNAME_REQUIRED";
    public static final String NICKNAME_TOO_LONG = "CHAT_AI_NICKNAME_TOO_LONG";
    public static final String BIO_TOO_LONG = "CHAT_AI_BIO_TOO_LONG";
    public static final String LANGUAGE_REQUIRED = "CHAT_AI_LANGUAGE_REQUIRED";
    public static final String LANGUAGE_TOO_LONG = "CHAT_AI_LANGUAGE_TOO_LONG";
    public static final String PERSONA_REQUIRED = "CHAT_AI_PERSONA_REQUIRED";
    public static final String PERSONA_TOO_LONG = "CHAT_AI_PERSONA_TOO_LONG";
    public static final String SETTING_INVALID = "CHAT_AI_SETTING_INVALID";
    public static final String IMAGE_MEMBER_PATH_INVALID = "CHAT_AI_IMAGE_MEMBER_PATH_INVALID";
    public static final String PROFILE_IMAGE_READ_FAILED = "CHAT_AI_PROFILE_IMAGE_READ_FAILED";

    private ChatAiErrorCode() {
    }
}
