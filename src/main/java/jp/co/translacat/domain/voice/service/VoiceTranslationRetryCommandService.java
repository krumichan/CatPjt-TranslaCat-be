package jp.co.translacat.domain.voice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.voice.dto.response.VoiceSegmentResponseDto;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.VoiceSegmentStatus;
import jp.co.translacat.domain.voice.mapper.VoiceResponseMapper;
import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.repository.VoiceSegmentRepository;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceModelResponse;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceTranslationRetryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 음성 번역 재시도 결과를 소유권과 원문 상태를 다시 확인한 뒤 저장한다. */
@Service
@RequiredArgsConstructor
public class VoiceTranslationRetryCommandService {
    private final VoiceSegmentRepository segmentRepository;
    private final VoiceResponseMapper responseMapper;
    private final ObjectMapper objectMapper;
    private final VoiceSourceReadingService sourceReadingService;

    /**
     * 실패한 음성 구간에 재시도 결과를 반영하고 갱신된 구간을 반환한다.
     *
     * @param userId 구간 소유자 ID
     * @param sessionId 구간이 속한 음성 세션 ID
     * @param segmentId 재시도할 구간 ID
     * @param context 번역 요청을 보내기 전에 확보한 원문 스냅샷
     * @param response 번역 공급자가 반환한 재시도 결과
     * @return 갱신된 구간과 읽기 정보
     * @throws BusinessException 구간이 없거나 재시도 중에 변경됐거나 번역문이 비어 있는 경우
     */
    @Transactional
    public VoiceSegmentResponseDto apply(
            Long userId,
            String sessionId,
            Long segmentId,
            VoiceTranslationRetryContext context,
            AiVoiceTranslationRetryResponse response) {
        // AI 호출 중 다른 요청이 확정한 결과를 덮어쓰지 않도록 잠금 상태에서 다시 검사한다.
        VoiceSegment segment =
                segmentRepository.findOwnedForUpdate(segmentId, sessionId, userId).orElseThrow(this::notFound);

        if (segment.getStatus() != VoiceSegmentStatus.TRANSLATION_FAILED
                || !context.sourceText().equals(segment.getSourceText())) {
            throw new BusinessException(
                    "Voice segment changed while translation retry was running.", VoiceErrorCode.RETRY_CONFLICT);
        }

        // 읽기는 원문 스냅샷에서 재생성한다. 일본어 분석에 실패하면 유효한 기존 읽기를 살린다.
        AiVoiceModelResponse model = response.model();
        String readingTokens =
                writeJson(sourceReadingService.forLanguage(context.sourceText(), context.sourceLanguage()));
        if ("ja".equals(context.sourceLanguage())) {
            // 일시적인 Sudachi 실패가 이전 확정 원문의 유효한 읽기까지 지우지 않게 한다.
            readingTokens = sourceReadingService.preserveStoredWhenGenerationFails(
                    readingTokens, segment.getSourceReadingTokens(), segment.getSourceText());
        }

        // 번역문을 검증한 뒤 읽기와 AI 버전 정보를 같은 구간에 함께 반영한다.
        segment.applyRetry(requireTranslatedText(response.translatedText()), readingTokens,
                response.translationSkipped(), model == null ? null : model.translationVersion(),
                model == null ? null : model.promptVersion());

        return responseMapper.toSegmentResponse(segment);
    }

    // 읽기 정보가 없으면 저장 형식에 맞춰 빈 JSON 배열로 직렬화한다.
    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(
                    node == null || node.isMissingNode() || node.isNull() ? objectMapper.createArrayNode() : node);
        } catch (Exception e) {
            throw invalidAiResponse("Invalid Reading token response from AI.");
        }
    }

    // 비어 있는 AI 응답으로 실패 구간이 완료되는 것을 막는다.
    private String requireTranslatedText(String translatedText) {
        if (translatedText == null || translatedText.isBlank()) {
            throw invalidAiResponse("AI translation retry response is invalid.");
        }
        return translatedText;
    }

    private BusinessException invalidAiResponse(String message) {
        return new BusinessException(message, VoiceErrorCode.INVALID_AI_RESPONSE);
    }

    private BusinessException notFound() {
        return new BusinessException("Voice segment was not found.", VoiceErrorCode.NOT_FOUND);
    }
}
