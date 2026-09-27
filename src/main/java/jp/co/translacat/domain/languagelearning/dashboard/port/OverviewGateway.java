package jp.co.translacat.domain.languagelearning.dashboard.port;

import java.util.List;
import java.util.Map;

/**
 * 인증된 사용자와 외부 DTO만 전달하며 집계·필터·이력 정책은 LL이 수행한다.
 */
public interface OverviewGateway {
    <T> T get(Long userId, String path, Map<String, ?> query, Class<T> type);

    <T> List<T> list(Long userId, String path, Map<String, ?> query, Class<T> type);
}
