package jp.co.translacat.domain.languagelearning.speaking.assistance.facade;

import jp.co.translacat.domain.languagelearning.speaking.assistance.dto.request.SpeakingAssistanceRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.assistance.dto.response.SpeakingAssistanceResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpeakingAssistanceFacade {

    private final SpeakingGateway gateway;

    public SpeakingAssistanceResponseDto get(
            Long userId,
            Long sessionId,
            SpeakingAssistanceRequestDto request
    ) {
        return gateway.post(userId, "/sessions/" + sessionId + "/assistance", request,
                SpeakingAssistanceResponseDto.class);
    }
}
