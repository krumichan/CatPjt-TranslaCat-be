package jp.co.translacat.global.utils;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;

@UtilityClass
public final class PublicIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    // 헷갈리는 문자 제거: O, 0, I, 1
    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    public static String generate() {
        return "TC-" + randomPart(4) + "-" + randomPart(4);
    }

    private static String randomPart(int length) {
        StringBuilder builder = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            builder.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }

        return builder.toString();
    }
}