package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NovelCatalogQueryService {
    private final NovelEmbeddedService embedded;
    public JsonNode snapshot(long actor, String platform, String id) {
        return embedded.catalogStatus(actor, platform, id);
    }
}
