package jp.co.translacat.domain.chat.openchat.support;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OpenChatPolicy {

    public static final int MIN_MAX_MEMBER_COUNT = 2;
    public static final int DEFAULT_MAX_MEMBER_COUNT = 50;
    public static final int MAX_MEMBER_COUNT_LIMIT = 100;

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_KEYWORD_LENGTH = 100;

    public static final int MEMBER_CODE_RANDOM_LENGTH = 5;
    public static final int MEMBER_CODE_MAX_ATTEMPTS = 20;

    public static final String PROFILE_IMAGE_OBJECT_KEY_PREFIX =
            "open-chat-profiles/";
}
