package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.model.WritingReportSnapshot;
import jp.co.translacat.domain.languagelearning.daily.port.DailyWritingGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningWritingClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class RemoteDailyWritingGateway implements DailyWritingGateway {
    private final ObjectProvider<LanguageLearningWritingClient> clients;

    public RemoteDailyWritingGateway(ObjectProvider<LanguageLearningWritingClient> clients) {
        this.clients = clients;
    }

    private LanguageLearningWritingClient client() {
        var value = clients.getIfAvailable();
        if (value == null) throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "LL_WRITING_REMOTE_DISABLED", "Writing 서비스 연결이 비활성화되어 있습니다.");
        return value;
    }

    public WritingReportSnapshot report(Long userId, LocalDate from, LocalDate to) {
        return client().report(userId, from, to);
    }

    @Override
    public DailyWritingSetResponseDto get(Long userId, Long setId) {
        return client().get(userId, setId);
    }

    @Override
    public Optional<DailyWritingSetResponseDto> findByDate(Long userId, LocalDate date, DailyWritingType type) {
        try {
            return Optional.of(client().history(userId, date, type));
        } catch (LanguageLearningServiceException failure) {
            if (failure.getStatus() == HttpStatus.NOT_FOUND
                    && "WRITING_SET_NOT_FOUND".equals(failure.getErrorCode())) return Optional.empty();
            throw failure;
        }
    }

    @Override
    public DailyWritingSetResponseDto create(Long userId, DailyWritingType writingType) {
        return client().create(userId, writingType);
    }

    @Override
    public DailyWritingSetResponseDto retry(Long userId, Long setId) {
        return client().retry(userId, setId);
    }

    @Override
    public DailyWritingSetResponseDto regenerate(Long userId, Long setId) {
        return client().regenerate(userId, setId);
    }

    @Override
    public AnswerResultResponseDto submit(Long userId, Long itemId, AnswerSubmitRequestDto request) {
        return client().submit(userId, itemId, request);
    }

    @Override
    public void resume(Long userId, Long itemId) {
        client().resume(userId, itemId);
    }
}
