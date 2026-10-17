package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.CatalogModels;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogQueryService {
    private final CatalogStore store;
    private final NovelRubyFormatter ruby;

    public CatalogQueryService(CatalogStore store, NovelRubyFormatter ruby) {
        this.store = store;
        this.ruby = ruby;
    }

    public CatalogModels.Snapshot snapshot(long actor, String id) {
        CatalogModels.Snapshot saved = store.snapshot(actor, id);
        // 저장한 원문·번역과 cache identity는 유지하고 화면의 ja 필드만 꾸민다.
        List<CatalogModels.Item> items = saved.items().stream().map(item -> new CatalogModels.Item(
                item.itemId(), item.identifier(), item.rank(), item.sourcePosition(), item.status(),
                display(item.title()), display(item.author()), display(item.synopsis()), display(item.statusText()),
                item.genreText(), item.isShortStory(), item.arrivalSequence(), item.errorCode(), item.cached())).toList();
        return new CatalogModels.Snapshot(saved.snapshotId(), saved.eventSequence(), saved.state(), saved.total(),
                saved.ready(), saved.failed(), saved.pageInfo(), saved.selector(), items, saved.traceId(), saved.sourceFetchMs());
    }

    private CatalogModels.Text display(CatalogModels.Text value) {
        // 원문 selector와 번역 목표 언어는 별개다. 기존 FE의 ko/en unit 계약을 유지하며
        // JA 원문을 번역문으로 채우지 않고 영어의 실제 번역·언어도 그대로 보존한다.
        boolean original = "ja".equals(value.targetLanguage());
        return new CatalogModels.Text(value.rawJa(), ruby.markup(value.rawJa()), original ? null : value.ko(),
                original ? null : value.translatedText(), original ? "ko" : value.targetLanguage());
    }
}
