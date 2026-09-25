package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeModeAvailabilityResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeSetResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeTodayModeStatusResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public PracticeSetResponseDto retryGeneration(Long userId, Long setId) {
        return queryService.toResponse(generationService.retry(userId, setId));
    }

    public List<PracticeTodayModeStatusResponseDto> getTodayStatus(
            Long userId,
            PracticeDomain domain
    ) {
        return queryService.getTodayStatus(userId, domain);
    }

    public List<PracticeModeAvailabilityResponseDto> availability(Long userId) {
        return generationService.availability(userId);
    }

    public PracticeAnswerResultResponseDto submit(
            Long userId,
            Long questionId,
            PracticeAnswerSubmitRequestDto request
    ) {
        return answerCommandService.submit(userId, questionId, request);
    }
}
