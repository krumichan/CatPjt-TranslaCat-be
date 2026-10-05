package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NovelCatalogFacade {
    private final NovelCatalogCommandService commands;
    private final NovelCatalogQueryService queries;
    public JsonNode start(String platform, NovelCatalogRequest request) { return commands.start(NovelReaderFacade.actor(), platform, request); }
    public JsonNode snapshot(String platform, String id) { return queries.snapshot(NovelReaderFacade.actor(), platform, id); }
    public JsonNode retry(String platform, String id, String item, NovelCatalogRequest.Retry request) {
        return commands.retry(NovelReaderFacade.actor(), platform, id, item, request);
    }
    public JsonNode cancel(String platform, String id) { return commands.cancel(NovelReaderFacade.actor(), platform, id); }
}
