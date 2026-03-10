package jp.co.translacat.infrastructure.client.legacy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyApiClientFacade {
    private final ExternalApiClient externalApiClient;
    private final GoogleProxy googleProxy;

    @Value("${external.use-proxy}")
    private boolean useProxy;

    public <T> T get(String uri, Class<T> responseType) {
        log.info("[LegacyApiClientFacade] Send Request....");
        log.info("[LegacyApiClientFacade] useProxy: {}, uri: {}", useProxy, uri);
        if (!useProxy) {
            return this.externalApiClient.get(uri, responseType);
        }

        T response = this.googleProxy.get(uri, responseType);

        this.printLog(response);

        return response;
    }

    private <T> void printLog(T response) {
        // ✨ 응답 결과 분석 로그
        if (response == null) {
            log.error("[LegacyApiClientFacade] <<< 응답 실패: 결과가 null입니다.");
        } else {
            String logContent = response.toString();
            int contentLength = logContent.length();

            String preview = contentLength > 100 ? logContent.substring(0, 100) + "..." : logContent;

            log.info("[LegacyApiClientFacade] <<< 응답 완료 - 길이: {}자", contentLength);
            log.info("[LegacyApiClientFacade] <<< 응답 데이터 미리보기: [{}]", preview);
        }
    }
}
