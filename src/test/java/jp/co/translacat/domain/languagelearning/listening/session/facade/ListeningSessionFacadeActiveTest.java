package jp.co.translacat.domain.languagelearning.listening.session.facade;

import jp.co.translacat.domain.languagelearning.listening.attempt.service.ListeningAttemptCommandService;
import jp.co.translacat.domain.languagelearning.listening.attempt.service.ListeningAttemptQueryService;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.playback.service.ListeningPlaybackCommandService;
import jp.co.translacat.domain.languagelearning.listening.report.service.ListeningEvaluationReportCommandService;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionCommandService;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionQueryService;

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
    private ListeningSessionCommandService sessionCommandService;
    @Mock
    private ListeningSessionQueryService sessionQueryService;
    @Mock
    private ListeningAttemptCommandService attemptCommandService;
    @Mock
    private ListeningAttemptQueryService attemptQueryService;
    @Mock
    private ListeningEvaluationReportCommandService reportCommandService;
    @Mock
    private ListeningPlaybackCommandService playbackCommandService;

    private ListeningSessionFacade facade;

    @BeforeEach
    void setUp() {
        facade = new ListeningSessionFacade(
                sessionCommandService,
                sessionQueryService,
                attemptCommandService,
                attemptQueryService,
                reportCommandService,
                playbackCommandService
        );
    }

    @Test
    void returnsInactiveViewWhenNoServerSessionExists() {
        when(sessionCommandService.activeSessionId(7L)).thenReturn(null);

        ListeningApiContract.ActiveSessionView result = facade.active(7L);

        assertThat(result.active()).isFalse();
        assertThat(result.session()).isNull();
    }

    @Test
    void returnsServerSessionWhenActiveSessionExists() {
        LocalDateTime now = LocalDateTime.now();
        ListeningApiContract.SessionView session =
                new ListeningApiContract.SessionView(
                        99L,
                        11L,
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
        when(sessionCommandService.activeSessionId(7L)).thenReturn(99L);
        when(sessionCommandService.synchronizeAndView(7L, 99L)).thenReturn(session);

        ListeningApiContract.ActiveSessionView result = facade.active(7L);

        assertThat(result.active()).isTrue();
        assertThat(result.session()).isSameAs(session);
    }
}
