package jp.co.translacat.infrastructure.client.ai.server;

import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {
    private final ExternalApiClient apiClient;

    @Value("${ai-server.url}")
    private String aiServerUrl;

    @Value("${ai-server.api-key}")
    private String apiKey;

    public List<String> callBatchTranslation(List<String> texts, String type) {
        String url = aiServerUrl + "/api/v1/translate/batch";

        Map<String, Object> request = new HashMap<>();
        request.put("texts", texts);
        request.put("type", type);

        Map<String, String> headers = Map.of("X-API-KEY", apiKey);

        try {
            var response = this.apiClient.post(url, request, headers, Map.class);

            if (response != null && response.containsKey("translated")) {
                return (List<String>) response.get("translated");
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("AI Server communication failed: {}", e.getMessage());
            throw new RuntimeException("AI Server Error", e);
        }
    }
}
