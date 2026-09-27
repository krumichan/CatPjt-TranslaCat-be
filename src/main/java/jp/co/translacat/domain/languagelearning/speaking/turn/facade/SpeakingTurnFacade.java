package jp.co.translacat.domain.languagelearning.speaking.turn.facade;

import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.request.SpeakingTurnProcessRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.request.SpeakingTurnUploadGrantRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnUploadGrantResponseDto;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SpeakingTurnFacade {
    private final SpeakingGateway gateway;

    public SpeakingTurnUploadGrantResponseDto createUploadGrant(Long userId, Long sessionId,
                                                                SpeakingTurnUploadGrantRequestDto request) {
        return gateway.post(userId, path(sessionId) + "/upload-url", request, SpeakingTurnUploadGrantResponseDto.class);
    }

    public SpeakingTurnUploadGrantResponseDto createRerecordUploadGrant(Long userId, Long sessionId, Long turnId) {
        return gateway.post(userId, path(sessionId) + "/" + turnId + "/rerecord/upload-url", null,
                SpeakingTurnUploadGrantResponseDto.class);
    }

    public SpeakingTurnResponseDto process(Long userId, Long sessionId, SpeakingTurnProcessRequestDto request,
                                           MultipartFile audio) {
        if (request == null || audio == null) throw new BusinessException("Audio 요청을 확인해 주세요.", "INVALID_AUDIO");

        // 외부 multipart를 내부 전송 DTO로 옮긴다. 형식·시간·재녹음 판정은 LL에서 수행한다.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request", request);
        body.put("contentType", audio.getContentType());
        body.put("fileName", audio.getOriginalFilename());
        try {
            body.put("audioBase64", Base64.getEncoder().encodeToString(audio.getBytes()));
        } catch (IOException failure) {
            throw new BusinessException("Audio 요청을 읽을 수 없습니다.", "INVALID_AUDIO");
        }

        return gateway.post(userId, path(sessionId), body, SpeakingTurnResponseDto.class);
    }

    public SpeakingTurnResponseDto get(Long userId, Long sessionId, Long turnId) {
        return gateway.get(userId, path(sessionId) + "/" + turnId, SpeakingTurnResponseDto.class);
    }

    public SpeakingTurnResponseDto retry(Long userId, Long sessionId, Long turnId) {
        return gateway.post(userId, path(sessionId) + "/" + turnId + "/retry", null, SpeakingTurnResponseDto.class);
    }

    public SpeakingTurnResponseDto exclude(Long userId, Long sessionId, Long turnId) {
        return gateway.post(userId, path(sessionId) + "/" + turnId + "/exclude", null, SpeakingTurnResponseDto.class);
    }

    private String path(Long sessionId) {
        return "/sessions/" + sessionId + "/turns";
    }
}
