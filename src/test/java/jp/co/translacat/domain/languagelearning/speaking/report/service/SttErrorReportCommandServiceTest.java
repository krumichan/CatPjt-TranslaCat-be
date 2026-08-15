package jp.co.translacat.domain.languagelearning.speaking.report.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.report.entity.SttErrorReport;
import jp.co.translacat.domain.languagelearning.speaking.report.repository.SttErrorReportRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionPolicySnapshotService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;
import jp.co.translacat.domain.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttErrorReportCommandServiceTest {

    @Mock
    private SttErrorReportRepository reportRepository;
    @Mock
    private SpeakingSessionQueryService sessionQueryService;
    @Mock
    private SpeakingTurnQueryService turnQueryService;
    @Mock
    private SpeakingSessionPolicySnapshotService policySnapshotService;
    @Mock
    private LanguageLearningJsonCodec jsonCodec;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SttErrorReport report;

    private SttErrorReportCommandService service;

    @BeforeEach
    void setUp() {
        service = new SttErrorReportCommandService(
                reportRepository,
                sessionQueryService,
                turnQueryService,
                policySnapshotService,
                jsonCodec,
                userRepository
        );
        when(reportRepository.findByIdAndUserId(701L, 7L))
                .thenReturn(Optional.of(report));
    }

    @Test
    void requestsSupportAfterReportCreation() {
        when(report.isSupportRequested()).thenReturn(false);
        when(report.getReportReference()).thenReturn("STT-ABC123");

        SttErrorReport result = service.requestSupport(7L, 701L);

        assertThat(result).isSameAs(report);
        verify(report).requestSupport("SUP-ABC123");
    }

    @Test
    void repeatedSupportRequestIsIdempotent() {
        when(report.isSupportRequested()).thenReturn(true);

        service.requestSupport(7L, 701L);

        verify(report, never()).requestSupport("SUP-ABC123");
    }
}
