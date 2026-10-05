package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NovelReaderCommandService {
    private final NovelEmbeddedService embedded;

    public record TranslationStart(String revision, String idempotencyKey, boolean retryFailed) {
        public TranslationStart {
            requireToken(revision, 128);
            requireToken(idempotencyKey, 128);
        }
    }
    public record AudioRequest(String revision, String segmentId, String language, Integer partIndex) {
        public AudioRequest {
            requireToken(revision, 128);
            requireToken(segmentId, 160);
            if (!java.util.Set.of("ja", "ko").contains(language == null ? "" : language)
                    || (partIndex != null && (partIndex < 0 || partIndex > 4095))) {
                throw new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false);
            }
        }
    }
    public record RepairRequest(String revision, String idempotencyKey, String segmentId) {
        public RepairRequest {
            requireToken(revision, 128);
            requireToken(idempotencyKey, 128);
            if (segmentId != null) requireToken(segmentId, 160);
        }
    }

    public JsonNode repair(long actor, NovelReaderAddress address, String jobId, RepairRequest request) {
        requireToken(jobId, 128);
        return embedded.repair(actor, address, jobId, request);
    }

    public record GlossaryUpdate(String expectedVersion, java.util.Map<String, String> terms) {
        public GlossaryUpdate {
            requireToken(expectedVersion, 128);
            if (terms == null || terms.size() > 100 || terms.entrySet().stream().anyMatch(entry ->
                    entry.getKey() == null || entry.getValue() == null || entry.getKey().isBlank()
                            || entry.getValue().isBlank() || entry.getKey().length() > 128 || entry.getValue().length() > 128)) {
                throw new NovelGatewayException(400, "NOVEL_GLOSSARY_INVALID", false);
            }
            terms = java.util.Map.copyOf(terms);
        }
    }

    public JsonNode start(long actor, NovelReaderAddress address, TranslationStart request) {
        return embedded.start(actor, address, request);
    }
    public JsonNode cancel(long actor, NovelReaderAddress address, String jobId) {
        // 취소도 조회와 같은 작업 ID 경계를 적용한다. 소유권·완료 상태는 소설 서비스가 검증한다.
        if (jobId == null || !jobId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false);
        }
        return embedded.cancel(actor, address, jobId);
    }
    public JsonNode audio(long actor, NovelReaderAddress address, AudioRequest request) {
        return embedded.audio(actor, address, request);
    }
    public JsonNode glossary(long actor, NovelReaderAddress address, GlossaryUpdate request) {
        return embedded.glossary(actor, address, request);
    }

    private static void requireToken(String value, int max) {
        if (value == null || value.isEmpty() || value.length() > max || !value.matches("[A-Za-z0-9:_-]+")) {
            throw new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false);
        }
    }
}
