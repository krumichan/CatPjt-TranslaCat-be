package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.infrastructure.novel.client.NovelCatalogSourceAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class NovelCatalogCommandService {
    private final NovelEmbeddedService embedded;
    private final NovelCatalogSourceAdapter source;

    public JsonNode start(long actor, String platform, NovelCatalogRequest request) {
        NovelCatalogRequest.platform(platform);
        // FE가 제공한 원문을 믿지 않는다. 제한된 공개 원문 adapter의 결과만 내부 기능에 전달한다.
        JsonNode raw = source.fetch(request);
        return embedded.catalogStart(actor, platform, request, raw);
    }
    public JsonNode retry(long actor, String platform, String id, String item, NovelCatalogRequest.Retry request) {
        return embedded.catalogRetry(actor, platform, id, item, request);
    }
    public JsonNode cancel(long actor, String platform, String id) {
        return embedded.catalogCancel(actor, platform, id);
    }
    static String path(String platform) {
        return "/internal/v1/novel/catalog/" + NovelCatalogRequest.platform(platform) + "/snapshots";
    }
}
