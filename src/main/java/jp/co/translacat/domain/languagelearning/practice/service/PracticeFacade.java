package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeSetResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeTodayModeStatusResponseDto;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PracticeFacade {
    private final PracticeGenerationService generationService;
    private final PracticeQueryService queryService;
    private final PracticeAnswerCommandService answerCommandService;

    public PracticeSetResponseDto getToday(
            Long userId,
            PracticeDomain domain,
            String mode
    ) {
        return queryService.toResponse(generationService.getOrGenerate(userId, domain, mode));
    }

    public PracticeSetResponseDto get(Long userId, Long setId) {
        return queryService.get(userId, setId);
    }

    public List<PracticeTodayModeStatusResponseDto> getTodayStatus(
            Long userId,
            PracticeDomain domain
    ) {
        return queryService.getTodayStatus(userId, domain);
    }

    public PracticeAnswerResultResponseDto submit(
            Long userId,
            Long questionId,
            PracticeAnswerSubmitRequestDto request
    ) {
        return answerCommandService.submit(userId, questionId, request);
    }
}
