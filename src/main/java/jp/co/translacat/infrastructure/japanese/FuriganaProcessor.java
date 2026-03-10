package jp.co.translacat.infrastructure.japanese;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class FuriganaProcessor {
    private final KuromojiFuriganaProcessor kuromojiFuriganaProcessor;
    private final SudachiFuriganaProcessor sudachiFuriganaProcessor;

    public String convertToRuby(String rawJa) {
        return this.convertToRuby(rawJa, true);
    }

    public String convertToRuby(String rawJa, boolean useSudachi) {
        return useSudachi
            ? sudachiFuriganaProcessor.convertToRuby(rawJa)
            : kuromojiFuriganaProcessor.convertToRuby(rawJa);
    }

    public String resolveDetailedRuby(String surface, String reading) {
        return this.resolveDetailedRuby(surface, reading, true);
    }

    public String resolveDetailedRuby(String surface, String reading, boolean useSudachi) {
        return useSudachi
            ? sudachiFuriganaProcessor.makeFineGrainedRuby(surface, reading)
            : kuromojiFuriganaProcessor.makeFineGrainedRuby(surface, reading);
    }

    public void syncCachedDict(Map<String, String> newDict) {
        kuromojiFuriganaProcessor.syncCachedDict(newDict);
        sudachiFuriganaProcessor.syncCachedDict(newDict);
    }
}
