package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayTrace;
import jp.co.translacat.novel.application.CatalogFacade;
import jp.co.translacat.novel.application.NovelFacade;
import jp.co.translacat.novel.application.RequestTrace;
import jp.co.translacat.novel.domain.NovelProblem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NovelEmbeddedTraceTest {
    private final CatalogFacade catalog = mock(CatalogFacade.class);
    private final NovelEmbeddedService service = new NovelEmbeddedService(
            mock(NovelFacade.class), catalog, new ObjectMapper());
    private static final String TRACE = "9285ab95-7ce4-44dc-aa4e-d79682267251";

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
        RequestTrace.clear();
    }

    @Test
    void embeddedCommandKeepsHttpTraceAndClearsThreadLocalAfterReturn() {
        // 준비: HTTP 요청과 내장 모듈이 같은 추적 ID를 사용해야 한다.
        bind(TRACE);
        when(catalog.snapshot(73, "catalog-id")).thenAnswer(invocation -> {
            assertThat(RequestTrace.current()).isNotNull();
            assertThat(RequestTrace.idOrNew()).isEqualTo(TRACE);
            return null;
        });

        // 실행
        service.catalogStatus(73, "syosetu", "catalog-id");

        // 검증: 재사용되는 요청 스레드에 이전 작업의 추적값을 남기지 않는다.
        verify(catalog).snapshot(73, "catalog-id");
        assertThat(RequestTrace.current()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "duplicate"})
    void invalidTraceNeverReachesEmbeddedCommand(String kind) {
        // 준비: 잘못된 값과 여러 header 값 모두 유료 작업 진입 전에 거절한다.
        var request = bind(kind.equals("malformed") ? "not-a-uuid" : TRACE);
        if (kind.equals("duplicate")) request.addHeader(NovelGatewayTrace.HEADER, TRACE);

        // 실행 / 검증
        assertThatThrownBy(() -> service.catalogStatus(73, "syosetu", "catalog-id"))
                .isInstanceOf(NovelGatewayException.class)
                .hasMessageContaining("NOVEL_TRACE_INVALID");
        verifyNoInteractions(catalog);
        assertThat(RequestTrace.current()).isNull();
    }

    @Test
    void domainFailureAlsoClearsRequestScope() {
        // 준비: 실패한 내장 호출도 예외 계약과 추적 수명 정리를 함께 보존한다.
        bind(TRACE);
        when(catalog.snapshot(73, "catalog-id")).thenAnswer(invocation -> {
            assertThat(RequestTrace.idOrNew()).isEqualTo(TRACE);
            throw new NovelProblem("CATALOG_NOT_FOUND", 404);
        });

        // 실행 / 검증
        assertThatThrownBy(() -> service.catalogStatus(73, "syosetu", "catalog-id"))
                .isInstanceOf(NovelGatewayException.class)
                .hasMessageContaining("CATALOG_NOT_FOUND");
        assertThat(RequestTrace.current()).isNull();
    }

    private MockHttpServletRequest bind(String id) {
        var request = new MockHttpServletRequest();
        request.addHeader(NovelGatewayTrace.HEADER, id);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }
}
