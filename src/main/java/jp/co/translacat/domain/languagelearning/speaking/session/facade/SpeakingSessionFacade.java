package jp.co.translacat.domain.languagelearning.speaking.session.facade;

import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingSessionDetailResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingSessionResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionCommandService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionCompletionCommandService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionLifecycleService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpeakingSessionFacade {

    private final SpeakingSessionCommandService sessionCommandService;
    private final SpeakingSessionCompletionCommandService completionCommandService;
    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingSessionLifecycleService lifecycleService;
    private final SpeakingTurnQueryService turnQueryService;

    public SpeakingSessionResponseDto create(
            Long userId,
            SpeakingSessionCreateRequestDto request
    ) {
        SpeakingSession session = sessionCommandService.create(userId, request);
        return sessionQueryService.toResponse(userId, session);
    }

    public SpeakingSessionResponseDto complete(
            Long userId,
            Long sessionId
    ) {
        SpeakingSession session = completionCommandService.complete(
                userId,
                sessionId
        );
        return sessionQueryService.toResponse(userId, session);
    }

    public SpeakingSessionDetailResponseDto get(
            Long userId,
            Long sessionId
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntity(
                userId,
                sessionId
        );
        lifecycleService.expireIfNeeded(session);

        return new SpeakingSessionDetailResponseDto(
                sessionQueryService.toResponse(userId, session),
                sessionQueryService.getDailyUsage(userId),
                turnQueryService.getResponses(userId, sessionId),
                lifecycleService.isResumable(session)
        );
    }

    public SpeakingSessionDetailResponseDto getActive(Long userId) {
        SpeakingSession session = sessionQueryService.findActiveEntity(userId)
                .orElse(null);
        if (session == null) {
            return null;
        }
        lifecycleService.expireIfNeeded(session);
        if (!session.isActive()) {
            return null;
        }
        return get(userId, session.getId());
    }
}
