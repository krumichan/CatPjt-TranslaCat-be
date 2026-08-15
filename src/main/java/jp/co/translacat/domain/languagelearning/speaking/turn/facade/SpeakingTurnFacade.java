package jp.co.translacat.domain.languagelearning.speaking.turn.facade;

import jp.co.translacat.domain.languagelearning.speaking.turn.dto.request.SpeakingTurnProcessRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.request.SpeakingTurnUploadGrantRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnUploadGrantResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnExclusionCommandService;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnProcessCommandService;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnUploadGrantCommandService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SpeakingTurnFacade {

    private final SpeakingTurnUploadGrantCommandService uploadGrantCommandService;
    private final SpeakingTurnProcessCommandService processCommandService;
    private final SpeakingTurnExclusionCommandService exclusionCommandService;
    private final SpeakingTurnQueryService queryService;

    public SpeakingTurnUploadGrantResponseDto createUploadGrant(
            Long userId,
            Long sessionId,
            SpeakingTurnUploadGrantRequestDto request
    ) {
        return uploadGrantCommandService.create(userId, sessionId, request);
    }

    public SpeakingTurnResponseDto process(
            Long userId,
            Long sessionId,
            SpeakingTurnProcessRequestDto request,
            MultipartFile audio
    ) {
        SpeakingTurn turn = processCommandService.process(
                userId,
                sessionId,
                request,
                audio
        );
        return queryService.toResponse(turn);
    }

    public SpeakingTurnResponseDto get(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        return queryService.toResponse(
                queryService.getOwnedEntity(userId, sessionId, turnId)
        );
    }

    public SpeakingTurnResponseDto retry(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        return queryService.toResponse(
                processCommandService.retry(userId, sessionId, turnId)
        );
    }

    public SpeakingTurnResponseDto exclude(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        return queryService.toResponse(
                exclusionCommandService.exclude(userId, sessionId, turnId)
        );
    }
}
