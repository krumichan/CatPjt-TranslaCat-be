package jp.co.translacat.infrastructure.client.ai.gemini;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AiGeminiClient {
    private final ChatClient chatClient;
    private final List<GoogleGenAiSafetySetting> safetySettings;

    public AiGeminiClient(ChatClient.Builder builder) {
        this.chatClient = builder.build();
        this.safetySettings = this.safetySettings();
    }

    @RateLimiter(name = "geminiLimiter")
    public String call(String rule, String data) {
        return call(rule, data, null);
    }

    @RateLimiter(name = "geminiLimiter")
    public String call(String rule, String data, String schema) {
        try {
            var optionsBuilder = GoogleGenAiChatOptions.builder()
                    .safetySettings(safetySettings);

            if (schema != null && !schema.isBlank()) {
                optionsBuilder.responseMimeType(MediaType.APPLICATION_JSON_VALUE)
                        .responseSchema(schema);
            } else {
                optionsBuilder.responseMimeType(MediaType.TEXT_PLAIN_VALUE);
            }

            return chatClient.prompt()
                .system(rule)
                .user(data)
                .options(optionsBuilder.build())
                .call()
                .content();
        } catch (Exception e) {
            log.error("Spring AI Gemini call failed: {}", e.getMessage());
            throw e;
        }
    }

    private List<GoogleGenAiSafetySetting> safetySettings() {
        return List.of(
            new GoogleGenAiSafetySetting(
                    GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_HARASSMENT,
                    GoogleGenAiSafetySetting.HarmBlockThreshold.BLOCK_NONE,
                    GoogleGenAiSafetySetting.HarmBlockMethod.HARM_BLOCK_METHOD_UNSPECIFIED
            ),
            new GoogleGenAiSafetySetting(
                    GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_HATE_SPEECH,
                    GoogleGenAiSafetySetting.HarmBlockThreshold.BLOCK_NONE,
                    GoogleGenAiSafetySetting.HarmBlockMethod.HARM_BLOCK_METHOD_UNSPECIFIED
            ),
            new GoogleGenAiSafetySetting(
                    GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT,
                    GoogleGenAiSafetySetting.HarmBlockThreshold.BLOCK_NONE,
                    GoogleGenAiSafetySetting.HarmBlockMethod.HARM_BLOCK_METHOD_UNSPECIFIED
            ),
            new GoogleGenAiSafetySetting(
                    GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT,
                    GoogleGenAiSafetySetting.HarmBlockThreshold.BLOCK_NONE,
                    GoogleGenAiSafetySetting.HarmBlockMethod.HARM_BLOCK_METHOD_UNSPECIFIED
            )
        );
    }
}
