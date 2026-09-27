package jp.co.translacat.domain.languagelearning.speaking.session.facade;

import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCompleteRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingPracticeModeStatusResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingSessionDetailResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingSessionResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingSessionFacade {
    private final SpeakingGateway gateway;

    public SpeakingSessionResponseDto create(Long userId, SpeakingSessionCreateRequestDto request) {
        return gateway.post(userId, "/sessions", request, SpeakingSessionResponseDto.class);
    }

    public SpeakingSessionResponseDto complete(Long userId, Long sessionId, SpeakingSessionCompleteRequestDto request) {
        return gateway.post(userId, "/sessions/" + sessionId + "/complete", request, SpeakingSessionResponseDto.class);
    }

    public SpeakingSessionDetailResponseDto get(Long userId, Long sessionId) {
        return gateway.get(userId, "/sessions/" + sessionId, SpeakingSessionDetailResponseDto.class);
    }

    public List<SpeakingPracticeModeStatusResponseDto> todayModeStatuses(Long userId) {
        return gateway.list(userId, "/sessions/today/status", SpeakingPracticeModeStatusResponseDto.class);
    }

    public SpeakingSessionDetailResponseDto getActive(Long userId) {
        return gateway.optional(userId, "/sessions/active", SpeakingSessionDetailResponseDto.class);
    }
}
