package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.*;
import jp.co.translacat.domain.languagelearning.practice.model.PracticeReportSnapshot;
import jp.co.translacat.domain.languagelearning.practice.port.PracticeGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningPracticeClient;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Primary
public class RemotePracticeGateway implements PracticeGateway {
    private final ObjectProvider<LanguageLearningPracticeClient> clients;

    public RemotePracticeGateway(ObjectProvider<LanguageLearningPracticeClient> clients) {
        this.clients = clients;
    }

    private LanguageLearningPracticeClient client() {
        var client = clients.getIfAvailable();
        if (client == null) throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "LL_PRACTICE_REMOTE_DISABLED", "Reading 서비스 연결이 비활성화되어 있습니다.");
        return client;
    }

    @Override
    public PracticeSetResponseDto today(Long userId, PracticeDomain domain, String mode) {
        return client().today(userId, domain, mode);
    }

    @Override
    public PracticeSetResponseDto get(Long userId, Long setId) {
        return client().get(userId, setId);
    }

    @Override
    public PracticeSetResponseDto retry(Long userId, Long setId) {
        return client().retry(userId, setId);
    }

    @Override
    public List<PracticeTodayModeStatusResponseDto> statuses(Long userId, PracticeDomain domain) {
        return client().statuses(userId, domain);
    }

    @Override
    public List<PracticeModeAvailabilityResponseDto> availability(Long userId) {
        return client().availability(userId);
    }

    @Override
    public PracticeAnswerResultResponseDto answer(Long userId, Long questionId,
                                                  PracticeAnswerSubmitRequestDto request) {
        return client().answer(userId, questionId, request);
    }

    @Override
    public VocabularyMasterySummaryResponseDto mastery(Long userId) {
        return client().mastery(userId);
    }

    public PracticeReportSnapshot report(Long userId, LocalDate from, LocalDate to) {
        return client().report(userId, from, to);
    }
}
