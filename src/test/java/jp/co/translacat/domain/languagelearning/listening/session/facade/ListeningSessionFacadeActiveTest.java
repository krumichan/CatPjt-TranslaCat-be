package jp.co.translacat.domain.languagelearning.listening.session.facade;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.port.ListeningGateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListeningSessionFacadeActiveTest {

    @Mock
    private ListeningGateway gateway;

    private ListeningSessionFacade facade;

    @BeforeEach
    void setUp() {
        facade = new ListeningSessionFacade(gateway);
    }

    @Test
    void returnsInactiveViewWhenNoServerSessionExists() {
        // 준비: 활성 여부 판정과 동기화는 LL이 처리한 동일 응답을 전달한다.
        when(gateway.get(7L, "/sessions/active", ListeningApiContract.ActiveSessionView.class))
                .thenReturn(new ListeningApiContract.ActiveSessionView(false, null));

        // 실행
        ListeningApiContract.ActiveSessionView result = facade.active(7L);

        // 검증
        assertThat(result.active()).isFalse();
        assertThat(result.session()).isNull();
    }

    @Test
    void returnsServerSessionWhenActiveSessionExists() {
        // 준비
        LocalDateTime now = LocalDateTime.now();
        ListeningApiContract.SessionView session =
                new ListeningApiContract.SessionView(
                        -99L,
                        -11L,
                        ListeningSessionStatus.IN_PROGRESS,
                        List.of(ListeningTaskType.DICTATION),
                        0,
                        0,
                        0L,
                        now,
                        now,
                        now.plusHours(2),
                        List.of(),
                        jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDailySetStatus.GENERATING,
                        5,
                        0,
                        null,
                        0,
                        true
                );
        when(gateway.get(7L, "/sessions/active", ListeningApiContract.ActiveSessionView.class))
                .thenReturn(new ListeningApiContract.ActiveSessionView(true, session));

        // 실행
        ListeningApiContract.ActiveSessionView result = facade.active(7L);

        // 검증
        assertThat(result.active()).isTrue();
        assertThat(result.session()).isSameAs(session);
    }
}
