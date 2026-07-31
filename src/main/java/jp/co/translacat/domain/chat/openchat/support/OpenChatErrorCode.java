package jp.co.translacat.domain.chat.openchat.support;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OpenChatErrorCode {

    public static final String REQUEST_REQUIRED =
            "OPEN_CHAT_REQUEST_REQUIRED";
    public static final String NAME_REQUIRED =
            "OPEN_CHAT_NAME_REQUIRED";
    public static final String NAME_TOO_LONG =
            "OPEN_CHAT_NAME_TOO_LONG";
    public static final String DESCRIPTION_REQUIRED =
            "OPEN_CHAT_DESCRIPTION_REQUIRED";
    public static final String DESCRIPTION_TOO_LONG =
            "OPEN_CHAT_DESCRIPTION_TOO_LONG";
    public static final String VISIBILITY_REQUIRED =
            "OPEN_CHAT_VISIBILITY_REQUIRED";
    public static final String MAX_MEMBER_COUNT_INVALID =
            "OPEN_CHAT_MAX_MEMBER_COUNT_INVALID";
    public static final String OWNER_PROFILE_REQUIRED =
            "OPEN_CHAT_OWNER_PROFILE_REQUIRED";
    public static final String NICKNAME_REQUIRED =
            "OPEN_CHAT_NICKNAME_REQUIRED";
    public static final String NICKNAME_TOO_LONG =
            "OPEN_CHAT_NICKNAME_TOO_LONG";
    public static final String PROFILE_IMAGE_OBJECT_KEY_INVALID =
            "OPEN_CHAT_PROFILE_IMAGE_OBJECT_KEY_INVALID";
    public static final String MEMBER_CODE_GENERATION_FAILED =
            "OPEN_CHAT_MEMBER_CODE_GENERATION_FAILED";
    public static final String ROOM_NOT_FOUND =
            "OPEN_CHAT_ROOM_NOT_FOUND";
    public static final String CURSOR_INVALID =
            "OPEN_CHAT_CURSOR_INVALID";
    public static final String PAGE_SIZE_INVALID =
            "OPEN_CHAT_PAGE_SIZE_INVALID";
    public static final String KEYWORD_TOO_LONG =
            "OPEN_CHAT_KEYWORD_TOO_LONG";
}
