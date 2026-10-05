package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.CatalogModels;
import org.springframework.stereotype.Service;

@Service
public class CatalogFacade {
    private final CatalogCommandService commands;
    private final CatalogQueryService queries;
    public CatalogFacade(CatalogCommandService commands, CatalogQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }
    public CatalogModels.Snapshot start(long actor, String platform, CatalogModels.Start request) {
        return queries.snapshot(actor, commands.start(actor, platform, request, RequestTrace.idOrNew()));
    }
    public CatalogModels.Snapshot snapshot(long actor, String id) { return queries.snapshot(actor, id); }
    public CatalogModels.Snapshot retry(long actor, String id, String item, CatalogModels.Retry request) {
        commands.retry(actor, id, item, request.idempotencyKey());
        return queries.snapshot(actor, id);
    }
    public CatalogModels.Snapshot cancel(long actor, String id) { return commands.cancel(actor, id); }
}
