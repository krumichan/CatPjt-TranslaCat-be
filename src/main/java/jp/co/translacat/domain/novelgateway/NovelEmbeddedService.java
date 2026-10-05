package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;
import jp.co.translacat.novel.application.CatalogFacade;
import jp.co.translacat.novel.application.NovelFacade;
import jp.co.translacat.novel.domain.CatalogModels;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/** 공개 BE 계약과 같은 JVM 안의 소설 기능 사이를 연결한다. */
@Service
public class NovelEmbeddedService {
    private final NovelFacade reader;
    private final CatalogFacade catalog;
    private final ObjectMapper json;

    public NovelEmbeddedService(NovelFacade reader, CatalogFacade catalog,
                                ObjectMapper json) {
        this.reader = reader;
        this.catalog = catalog;
        this.json = json;
    }

    public JsonNode reader(long actor, NovelReaderAddress address) {
        return call(() -> reader.reader(key(address), actor).observed());
    }

    public JsonNode status(long actor, NovelReaderAddress address, String jobId) {
        return call(() -> reader.status(key(address), actor, jobId).observed());
    }

    public JsonNode start(long actor, NovelReaderAddress address,
                          NovelReaderCommandService.TranslationStart request) {
        return call(() -> reader.start(key(address), actor, request.revision(),
                request.idempotencyKey(), request.retryFailed()).observed());
    }

    public JsonNode repair(long actor, NovelReaderAddress address, String jobId,
                           NovelReaderCommandService.RepairRequest request) {
        return call(() -> reader.repair(key(address), actor, jobId, request.revision(),
                request.idempotencyKey(), request.segmentId()).observed());
    }

    public JsonNode cancel(long actor, NovelReaderAddress address, String jobId) {
        return call(() -> reader.cancel(key(address), actor, jobId).observed());
    }

    public JsonNode glossary(long actor, NovelReaderAddress address) {
        return call(() -> reader.glossary(key(address), actor));
    }

    public JsonNode glossary(long actor, NovelReaderAddress address,
                             NovelReaderCommandService.GlossaryUpdate request) {
        return call(() -> reader.glossary(key(address), actor,
                request.expectedVersion(), request.terms()));
    }

    public JsonNode audio(long actor, NovelReaderAddress address,
                          NovelReaderCommandService.AudioRequest request) {
        return call(() -> reader.audio(key(address), actor, request.revision(), request.segmentId(),
                request.language(), request.partIndex() == null ? 0 : request.partIndex()));
    }

    public JsonNode catalogStart(long actor, String platform, NovelCatalogRequest request,
                                 JsonNode source) {
        return call(() -> {
            CatalogModels.Selector selector = json.convertValue(request.selector(), CatalogModels.Selector.class);
            CatalogModels.SourcePage page = json.convertValue(source, CatalogModels.SourcePage.class);
            return catalog.start(actor, platform,
                    new CatalogModels.Start(selector, request.idempotencyKey(), page));
        });
    }

    public JsonNode catalogStatus(long actor, String platform, String id) {
        NovelCatalogRequest.platform(platform);
        return call(() -> catalog.snapshot(actor, NovelCatalogRequest.token(id)));
    }

    public JsonNode catalogRetry(long actor, String platform, String id, String item,
                                 NovelCatalogRequest.Retry request) {
        NovelCatalogRequest.platform(platform);
        return call(() -> catalog.retry(actor, NovelCatalogRequest.token(id),
                NovelCatalogRequest.token(item), new CatalogModels.Retry(request.idempotencyKey())));
    }

    public JsonNode catalogCancel(long actor, String platform, String id) {
        NovelCatalogRequest.platform(platform);
        return call(() -> catalog.cancel(actor, NovelCatalogRequest.token(id)));
    }

    private EpisodeKey key(NovelReaderAddress address) {
        return new EpisodeKey(address.platform(), address.novel(), address.episode());
    }

    private JsonNode call(Supplier<?> command) {
        // 같은 JVM의 소설 기능을 호출한다. actor와 ADMIN 검증은 공개 facade가 담당한다.
        try {
            return json.valueToTree(command.get());
        } catch (NovelProblem problem) {
            throw new NovelGatewayException(problem.status(), problem.code(), problem.retryable());
        } catch (IllegalArgumentException problem) {
            throw new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false);
        }
    }
}
