package jp.co.translacat.infrastructure.client.ai.server;

import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;

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

    public String callFileConversion(MultipartFile file) {
        String url = aiServerUrl + "/api/v1/stt/transcribe";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getResource()).filename(file.getOriginalFilename());

        try {
            var response = this.apiClient.postMultipart(url, builder.build(), this.basicHeader(), Map.class);
            if (response != null) {
                return String.valueOf(response.get("text"));
            }

            return "";
        } catch (Exception e) {
            log.error("AI Server communication failed: {}", e.getMessage());
            throw new AiServerCommunicationException("AI Server Error", e);
        }
    }

    public List<String> callBatchTranslation(List<String> texts, String type) {
        String url = aiServerUrl + "/api/v1/translate/batch";

        Map<String, Object> request = new HashMap<>();
        request.put("texts", texts);
        request.put("type", type);


        try {
            var response = this.apiClient.post(url, request, this.basicHeader(), Map.class);

            if (response != null && response.containsKey("translated")) {
                return (List<String>) response.get("translated");
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("AI Server communication failed: {}", e.getMessage());
            throw new AiServerCommunicationException("AI Server Error", e);
        }
    }

    private Map<String, String> basicHeader() {
        return Map.of("X-API-KEY", apiKey);
    }
}
