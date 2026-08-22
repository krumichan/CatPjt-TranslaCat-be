package jp.co.translacat.domain.voice.support;

import lombok.experimental.UtilityClass;

import java.util.Set;

@UtilityClass
public class VoicePolicy {

    public static final Set<String> SUPPORTED_LANGUAGES =
            Set.of("ko", "ja", "en");

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    public static final int PCM_S16LE_SAMPLE_RATE = 16_000;
    public static final int PCM_S16LE_CHANNELS = 1;
    public static final int PCM_S16LE_BYTES_PER_MILLISECOND = 32;
}
