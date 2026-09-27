package jp.co.translacat.domain.languagelearning.daily.facade;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.port.DailyWritingGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DailyWritingFacadeCutoverTest {
    private final DailyWritingGateway gateway = mock(DailyWritingGateway.class);
    private final DailyWritingFacade facade = new DailyWritingFacade(gateway);
    private final LocalDate day = LocalDate.of(2026, 9, 26);

    @Test
    void generationSendsOnlyAuthenticatedUserAndMode() {
        // 준비: LL이 기존 세트를 찾거나 최초 snapshot을 원자적으로 생성한다.
        var existing = mock(DailyWritingSetResponseDto.class);
        when(gateway.create(123L, DailyWritingType.FREE)).thenReturn(existing);

        // 실행: 기존 Controller가 호출하는 Facade를 통과한다.
        var result = facade.getOrGenerateToday(123L, DailyWritingType.FREE);

        // 검증: BE가 날짜·키워드·프로필 문맥을 조립하는 다른 호출을 만들지 않는다.
        assertSame(existing, result);
        verify(gateway).create(123L, DailyWritingType.FREE);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void historyReadsOnlyLlData() {
        // 준비: 새 LL 이력을 조회한다.
        var existing = mock(DailyWritingSetResponseDto.class);
        when(gateway.findByDate(123L, day, DailyWritingType.FREE)).thenReturn(Optional.of(existing));

        // 실행
        var result = facade.getHistory(123L, day, DailyWritingType.FREE);

        // 검증
        assertSame(existing, result);
        verify(gateway).findByDate(123L, day, DailyWritingType.FREE);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void remoteFailureDoesNotCreateCoreSet() {
        // 준비: LL 조회가 연결 오류를 반환한다.
        var failure = new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY,
                "LL_WRITING_UNAVAILABLE", "합성 연결 오류");
        when(gateway.create(123L, DailyWritingType.FREE)).thenThrow(failure);

        // 실행·검증: 오류를 그대로 전달하고 다른 생성 경로를 호출하지 않는다.
        assertSame(failure, assertThrows(LanguageLearningServiceException.class,
                () -> facade.getOrGenerateToday(123L, DailyWritingType.FREE)));
        verify(gateway).create(123L, DailyWritingType.FREE);
        verifyNoMoreInteractions(gateway);
    }
}
