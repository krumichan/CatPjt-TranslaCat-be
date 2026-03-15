package jp.co.translacat.infrastructure.client.ai;

import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ConversionExecutor {

    private final AiServerClient aiServerClient;

    public String stt(MultipartFile file) {
        return aiServerClient.callFileConversion(file);
    }
}
