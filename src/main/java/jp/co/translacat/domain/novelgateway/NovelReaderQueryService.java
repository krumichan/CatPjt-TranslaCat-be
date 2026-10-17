package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.user.enums.RecentViewType;
import jp.co.translacat.domain.user.service.RecentViewService;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;
import jp.co.translacat.novel.domain.NovelTargetLanguage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NovelReaderQueryService {
    private final NovelEmbeddedService embedded;
    private final RecentViewService recentViews;

    public NovelReaderQueryService(NovelEmbeddedService embedded) {
        this(embedded, null);
    }

    @Autowired
    public NovelReaderQueryService(NovelEmbeddedService embedded, RecentViewService recentViews) {
        this.embedded = embedded;
        this.recentViews = recentViews;
    }

    public JsonNode reader(long actor, NovelReaderAddress address) {
        return recordVisit(address, embedded.reader(actor, address), "ko");
    }
    public JsonNode reader(long actor, NovelReaderAddress address, String targetLanguage) {
        return recordVisit(address, embedded.reader(actor, address, targetLanguage), targetLanguage);
    }

    private JsonNode recordVisit(NovelReaderAddress address, JsonNode snapshot, String targetLanguage) {
        String target = NovelTargetLanguage.normalize(targetLanguage);
        if (recentViews == null || snapshot == null || !snapshot.path("title").path("rawJa").isTextual()) return snapshot;
        String actualTarget = snapshot.path("targetLanguage").asText("ko");
        if (!target.equals(actualTarget)) throw new NovelGatewayException(409, "TRANSLATION_TARGET_MISMATCH", false);

        // 최초 원문 조회만 방문을 기록한다. status poll·번역·복구 조회는 viewedAt을 바꾸지 않는다.
        boolean shortStory = java.util.Set.of("short", "0").contains(address.episode());
        JsonNode title = snapshot.path("title");
        PlatformCode code = "kakuyomu".equalsIgnoreCase(address.platform()) ? PlatformCode.KAKUYOMU : PlatformCode.SYOSETU;
        recentViews.save(code, shortStory ? RecentViewType.SHORT : RecentViewType.EPISODE,
                address.novel(), shortStory ? null : address.episode(), title.path("rawJa").asText(),
                title.path("ja").asText(null), "ko".equals(target) ? title.path("ko").asText(null) : null,
                "en".equals(target) ? title.path("translatedText").asText(null) : null, target);
        return snapshot;
    }
    public JsonNode glossary(long actor, NovelReaderAddress address) {
        return embedded.glossary(actor, address);
    }
    public JsonNode glossary(long actor, NovelReaderAddress address, String targetLanguage) {
        return embedded.glossary(actor, address, targetLanguage);
    }
    public JsonNode status(long actor, NovelReaderAddress address, String jobId) {
        if (jobId == null || !jobId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false);
        }
        return embedded.status(actor, address, jobId);
    }
}
