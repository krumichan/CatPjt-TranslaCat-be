package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NovelReaderQueryService {
    private final NovelEmbeddedService embedded;

    public JsonNode reader(long actor, NovelReaderAddress address) {
        return embedded.reader(actor, address);
    }
    public JsonNode glossary(long actor, NovelReaderAddress address) {
        return embedded.glossary(actor, address);
    }
    public JsonNode status(long actor, NovelReaderAddress address, String jobId) {
        if (jobId == null || !jobId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false);
        }
        return embedded.status(actor, address, jobId);
    }
}
