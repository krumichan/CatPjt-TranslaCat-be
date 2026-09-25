package jp.co.translacat.domain.languagelearning.level.facade;

import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.port.LevelTestGateway;
import jp.co.translacat.domain.languagelearning.profile.service.LevelTestBaselineBridge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class LanguageLearningLevelTestFacadeTest {
    @Test
    void finalResponseIsNotReturnedBeforeBaselineIsApplied() {
        var g = mock(LevelTestGateway.class);
        var b = mock(LevelTestBaselineBridge.class);
        var f = new LanguageLearningLevelTestFacade(g, b);
        var request = new LevelAnswerRequestDto(null, List.of(), "回答", "key");
        var result = new LevelAnswerResultResponseDto(1L, 20L, 20, true, 80, null, true, null);
        when(g.submit(123L, 1L, 20L, request)).thenReturn(result);
        assertSame(result, f.submit(123L, 1L, 20L, request));
        var order = inOrder(g, b);
        order.verify(g).submit(123L, 1L, 20L, request);
        order.verify(b).requireCompleted(123L);
    }

    @Test
    void nonFinalAnswerDoesNotApplyProfile() {
        var g = mock(LevelTestGateway.class);
        var b = mock(LevelTestBaselineBridge.class);
        var f = new LanguageLearningLevelTestFacade(g, b);
        var request = new LevelAnswerRequestDto("A", List.of(), null, "key");
        when(g.submit(123L, 1L, 1L, request)).thenReturn(
                new LevelAnswerResultResponseDto(1L, 1L, 2, true, 100, null, false, null));
        f.submit(123L, 1L, 1L, request);
        verifyNoInteractions(b);
    }
}
