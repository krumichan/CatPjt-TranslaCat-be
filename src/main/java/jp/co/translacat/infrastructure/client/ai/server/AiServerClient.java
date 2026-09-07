package jp.co.translacat.infrastructure.client.ai.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyRequestDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestQuestionRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestTextEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiWritingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.level.pool.support.LevelTestPoolGenerationRejectedException;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingAssistanceRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingSessionStartRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingTtsRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingTurnProcessRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingAssistanceResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingConversationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingSessionStartResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingTtsResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingTurnProcessResponseDto;
import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiChatTranslationRequest;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiChatTranslationResponse;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisOptions;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisResponse;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient4xxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {
    private final ExternalApiClient apiClient;
    private final ObjectMapper objectMapper;

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

    public AiReceiptAnalysisResponse callReceiptAnalysis(
            MultipartFile file,
            AiReceiptAnalysisOptions options
    ) {
        String url = aiServerUrl + "/api/v1/account-book/receipts/analyze";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getResource())
                .filename(file.getOriginalFilename());

        if (options != null) {
            builder.part("options", toJson(options))
                    .contentType(MediaType.TEXT_PLAIN);
        }

        try {
            return this.apiClient.postMultipart(
                    url,
                    builder.build(),
                    this.basicHeader(),
                    AiReceiptAnalysisResponse.class
            );
        } catch (Exception e) {
            log.error("AI Server receipt analysis failed: {}", e.getMessage());

            throw new AiServerCommunicationException("AI Server Receipt Analysis Error", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("AI Server 요청 옵션 생성에 실패했습니다.");
        }
    }

    public AiChatTranslationResponse callChatTranslation(
            String text,
            String targetLanguageCode
    ) {
        String url = aiServerUrl + "/api/v1/chat/translate";

        AiChatTranslationRequest request =
                new AiChatTranslationRequest(
                        text,
                        targetLanguageCode
                );

        try {
            return this.apiClient.post(
                    url,
                    request,
                    this.basicHeader(),
                    AiChatTranslationResponse.class
            );
        } catch (Exception e) {
            log.error("AI Server chat translation failed: {}", e.getMessage());

            throw new AiServerCommunicationException(
                    "AI Server Chat Translation Error",
                    e
            );
        }
    }

    public ChatAiReplyResponseDto callChatAiReply(
            ChatAiReplyRequestDto request
    ) {
        String url = aiServerUrl + "/api/v1/chat/ai/reply";

        try {
            return this.apiClient.post(
                    url,
                    request,
                    this.basicHeader(),
                    ChatAiReplyResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server chat reply failed. requestId={}, cause={}",
                    request == null ? null : request.requestId(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Chat Reply Error",
                    e
            );
        }
    }

    public AiPracticeGenerationResponseDto callLanguageLearningPracticeGeneration(
            AiPracticeGenerationRequestDto request
    ) {
        String url = aiServerUrl + "/api/v1/language-learning/practice/generate";
        try {
            return this.apiClient.postOnce(
                    url, request, this.basicHeader(), AiPracticeGenerationResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server Reading/Vocabulary generation failed. requestId={}, cause={}",
                    request == null ? null : request.requestId(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Reading/Vocabulary Generation Error", e
            );
        }
    }

    public AiDailyWritingGenerationResponseDto callLanguageLearningDailyGeneration(
            AiDailyWritingGenerationRequestDto request
    ) {
        String url = aiServerUrl
                + "/api/v1/language-learning/writing/daily/generate";

        try {
            return this.apiClient.postOnce(
                    url,
                    request,
                    this.basicHeader(),
                    AiDailyWritingGenerationResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server language learning daily generation failed. "
                            + "requestId={}, cause={}",
                    request == null ? null : request.requestId(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Language Learning Daily Generation Error",
                    e
            );
        }
    }

    public AiWritingEvaluationResponseDto callLanguageLearningEvaluation(
            AiWritingEvaluationRequestDto request
    ) {
        String url = aiServerUrl
                + "/api/v1/language-learning/writing/evaluate";

        try {
            return this.apiClient.postOnce(
                    url,
                    request,
                    this.basicHeader(),
                    AiWritingEvaluationResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server language learning evaluation failed. "
                            + "requestId={}, cause={}",
                    request == null ? null : request.requestId(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Language Learning Evaluation Error",
                    e
            );
        }
    }

    public AiLevelTestQuestionResponseDto callLanguageLearningLevelTestQuestion(
            AiLevelTestQuestionRequestDto request
    ) {
        return postLanguageLearning(
                "/api/v1/language-learning/level-test/questions/generate",
                request,
                AiLevelTestQuestionResponseDto.class,
                "Level Test Question"
        );
    }

    public AiLevelTestQuestionResponseDto callLanguageLearningLevelTestPoolQuestion(
            AiLevelTestQuestionRequestDto request
    ) {
        String path = "/api/v1/language-learning/level-test/questions/generate";
        try {
            return apiClient.postOnceLevelTestPool(
                    aiServerUrl + path,
                    request,
                    basicHeader(),
                    AiLevelTestQuestionResponseDto.class
            );
        } catch (Exception e) {
            LevelTestPoolGenerationRejectedException rejected =
                    toLevelTestPoolGenerationRejectedException(e);
            if (rejected != null) {
                throw rejected;
            }
            log.error(
                    "AI Server language learning Level Test Pool Question failed. cause={}",
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Language Learning Level Test Pool Question Error",
                    e
            );
        }
    }

    private LevelTestPoolGenerationRejectedException
            toLevelTestPoolGenerationRejectedException(Throwable throwable) {
        ExternalApiClient4xxException clientError = findCause(
                throwable,
                ExternalApiClient4xxException.class
        );
        if (clientError == null
                || clientError.getResponseException().getRawStatusCode() != 422) {
            return null;
        }

        String responseBody = clientError.getResponseException()
                .getResponseBodyAsString();
        try {
            JsonNode detail = objectMapper.readTree(responseBody).path("detail");
            if (!detail.isObject()) {
                return null;
            }
            String code = detail.path("code").asText("");
            if (!Set.of(
                    "QUESTION_CONTENT_INVALID",
                    "CONTENT_DIVERSITY_EXHAUSTED"
            ).contains(code)) {
                return null;
            }

            List<String> reasons = new java.util.ArrayList<>();
            JsonNode reasonsNode = detail.path("reasons");
            if (reasonsNode.isArray()) {
                reasonsNode.forEach(node -> {
                    if (node.isTextual() && !node.asText().isBlank()) {
                        reasons.add(node.asText());
                    }
                });
            }
            if (reasons.isEmpty()) {
                String message = detail.path("message").asText("");
                if (!message.isBlank()) {
                    reasons.add(message);
                }
            }
            return new LevelTestPoolGenerationRejectedException(
                    code,
                    reasons,
                    throwable
            );
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private <T extends Throwable> T findCause(
            Throwable throwable,
            Class<T> type
    ) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    public AiLevelTestEvaluationResponseDto callLanguageLearningLevelTestTextEvaluation(
            AiLevelTestTextEvaluationRequestDto request
    ) {
        return postLanguageLearning(
                "/api/v1/language-learning/level-test/evaluate/text",
                request,
                AiLevelTestEvaluationResponseDto.class,
                "Level Test Text Evaluation"
        );
    }

    public AiLevelTestEvaluationResponseDto callLanguageLearningLevelTestSpeakingEvaluation(
            AiLevelTestSpeakingEvaluationRequestDto request,
            byte[] audioBytes,
            String fileName,
            String contentType
    ) {
        String url = aiServerUrl
                + "/api/v1/language-learning/level-test/evaluate/speaking";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("context", toJson(request))
                .contentType(MediaType.APPLICATION_JSON);

        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return fileName == null || fileName.isBlank()
                        ? "level-test-speaking.webm"
                        : fileName;
            }
        };
        builder.part("audio", resource)
                .filename(resource.getFilename())
                .contentType(MediaType.parseMediaType(
                        contentType == null || contentType.isBlank()
                                ? "application/octet-stream"
                                : contentType
                ));

        try {
            return apiClient.postMultipartOnce(
                    url,
                    builder.build(),
                    basicHeader(),
                    AiLevelTestEvaluationResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server level test speaking evaluation failed. "
                            + "requestId={}, cause={}",
                    request == null ? null : request.requestId(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Level Test Speaking Evaluation Error",
                    e
            );
        }
    }

    private <T, R> R postLanguageLearning(
            String path,
            T request,
            Class<R> responseType,
            String operation
    ) {
        try {
            return apiClient.postOnce(
                    aiServerUrl + path,
                    request,
                    basicHeader(),
                    responseType
            );
        } catch (Exception e) {
            log.error(
                    "AI Server language learning {} failed. cause={}",
                    operation,
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Language Learning " + operation + " Error",
                    e
            );
        }
    }

    public AiSpeakingSessionStartResponseDto callSpeakingSessionStart(
            AiSpeakingSessionStartRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/sessions/start",
                request,
                AiSpeakingSessionStartResponseDto.class,
                "Speaking Session Start"
        );
    }

    public AiSpeakingTurnProcessResponseDto callSpeakingTurnProcess(
            AiSpeakingTurnProcessRequestDto request,
            byte[] audioBytes,
            String fileName,
            String contentType
    ) {
        String url = aiServerUrl
                + "/api/v1/language-learning/speaking/turns/process";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("context", toJson(request))
                .contentType(MediaType.TEXT_PLAIN);

        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return fileName == null || fileName.isBlank()
                        ? "speaking-audio.wav"
                        : fileName;
            }
        };

        builder.part("audio", resource)
                .filename(resource.getFilename())
                .contentType(MediaType.parseMediaType(
                        contentType == null || contentType.isBlank()
                                ? "application/octet-stream"
                                : contentType
                ));

        try {
            return apiClient.postMultipartOnce(
                    url,
                    builder.build(),
                    basicHeader(),
                    AiSpeakingTurnProcessResponseDto.class
            );
        } catch (Exception e) {
            log.error(
                    "AI Server speaking turn processing failed. "
                            + "requestId={}, sessionId={}, turnIndex={}, cause={}",
                    request == null ? null : request.requestId(),
                    request == null ? null : request.sessionId(),
                    request == null ? null : request.turnIndex(),
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Speaking Turn Error",
                    e
            );
        }
    }

    public AiSpeakingConversationResponseDto callSpeakingResponse(
            AiSpeakingTurnProcessRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/turns/respond",
                request,
                AiSpeakingConversationResponseDto.class,
                "Speaking Conversation"
        );
    }

    public AiSpeakingAssistanceResponseDto callSpeakingAssistance(
            AiSpeakingAssistanceRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/assistance",
                request,
                AiSpeakingAssistanceResponseDto.class,
                "Speaking Assistance"
        );
    }

    public AiSpeakingTtsResponseDto callSpeakingTts(
            AiSpeakingTtsRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/tts",
                request,
                AiSpeakingTtsResponseDto.class,
                "Speaking TTS"
        );
    }

    public AiSpeakingEvaluationResponseDto callSpeakingEvaluation(
            AiSpeakingEvaluationRequestDto request
    ) {
        return postSpeaking(
                "/api/v1/language-learning/speaking/evaluate",
                request,
                AiSpeakingEvaluationResponseDto.class,
                "Speaking Evaluation"
        );
    }

    public byte[] callSpeakingAudio(String audioReference) {
        String url = aiServerUrl
                + "/api/v1/language-learning/speaking/audio/"
                + audioReference;

        try {
            return apiClient.getBytesOnce(url, basicHeader());
        } catch (Exception e) {
            log.error(
                    "AI Server speaking audio download failed. "
                            + "audioReference={}, cause={}",
                    audioReference,
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server Speaking Audio Error",
                    e
            );
        }
    }

    private <T, R> R postSpeaking(
            String path,
            T request,
            Class<R> responseType,
            String operation
    ) {
        String url = aiServerUrl + path;

        try {
            return apiClient.postOnce(
                    url,
                    request,
                    basicHeader(),
                    responseType
            );
        } catch (Exception e) {
            log.error(
                    "AI Server {} failed. cause={}",
                    operation,
                    e.getMessage()
            );
            throw new AiServerCommunicationException(
                    "AI Server " + operation + " Error",
                    e
            );
        }
    }

}
