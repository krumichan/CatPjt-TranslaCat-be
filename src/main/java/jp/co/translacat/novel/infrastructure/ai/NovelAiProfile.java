package jp.co.translacat.novel.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AI가 허용한 profile을 명시 선택한다. 공통 tier 변경이나 자동 fallback은 하지 않는다. */
@Component
public record NovelAiProfile(String id, String version, String reasoningEffort, String serviceTier, int maxOutputTokens) {
    public NovelAiProfile(@Value("${novel.ai.profile-id:}") String id,
                          @Value("${novel.ai.profile-version:}") String version,
                          @Value("${novel.ai.profile-reasoning:none}") String reasoningEffort,
                          @Value("${novel.ai.profile-service-tier:default}") String serviceTier,
                          @Value("${novel.ai.profile-output-tokens:32768}") int maxOutputTokens) {
        if (id == null || version == null || !id.matches("[A-Za-z0-9._-]{0,100}")
                || !version.matches("[A-Za-z0-9._-]{0,64}") || !id.isBlank() && version.isBlank()
                || !reasoningEffort.matches("none|minimal|low|medium|high|xhigh")
                || !serviceTier.matches("default|auto|priority|flex") || maxOutputTokens < 1024 || maxOutputTokens > 32768) {
            throw new IllegalArgumentException("NOVEL_AI_PROFILE_INVALID");
        }
        this.id = id; this.version = version; this.reasoningEffort = reasoningEffort;
        this.serviceTier = serviceTier; this.maxOutputTokens = maxOutputTokens;
    }
    public static NovelAiProfile legacy() { return new NovelAiProfile("", "", "none", "default", 8192); }
}
