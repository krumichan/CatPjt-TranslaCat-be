package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.CatalogModels;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import org.springframework.stereotype.Service;

@Service
public class CatalogQueryService {
    private final CatalogStore store;
    public CatalogQueryService(CatalogStore store) { this.store = store; }
    public CatalogModels.Snapshot snapshot(long actor, String id) { return store.snapshot(actor, id); }
}
