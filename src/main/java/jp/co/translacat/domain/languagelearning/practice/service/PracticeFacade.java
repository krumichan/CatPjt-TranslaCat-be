package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeModeAvailabilityResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeSetResponseDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.PracticeTodayModeStatusResponseDto;
import jp.co.translacat.domain.languagelearning.practice.port.PracticeGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PracticeFacade {
    private final PracticeGateway gateway;

    public PracticeSetResponseDto getToday(
            Long userId,
            PracticeDomain domain,
            String mode
    ) {
        return gateway.today(userId, domain, mode);
    }

    public PracticeSetResponseDto get(Long userId, Long setId) {
        return gateway.get(userId, setId);
    }

    public PracticeSetResponseDto retryGeneration(Long userId, Long setId) {
        return gateway.retry(userId, setId);
    }

    public List<PracticeTodayModeStatusResponseDto> getTodayStatus(
            Long userId,
            PracticeDomain domain
    ) {
        return gateway.statuses(userId, domain);
    }

    public List<PracticeModeAvailabilityResponseDto> availability(Long userId) {
        return gateway.availability(userId);
    }

    public PracticeAnswerResultResponseDto submit(
            Long userId,
            Long questionId,
            PracticeAnswerSubmitRequestDto request
    ) {
        return gateway.answer(userId, questionId, request);
    }
}
