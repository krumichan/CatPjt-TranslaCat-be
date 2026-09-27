package jp.co.translacat.infrastructure.languagelearning.growth;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthActivitySnapshot;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RemoteGrowthGateway implements GrowthReadGateway {
    private final GrowthProperties properties;
    private final ObjectProvider<GrowthHttpClient> clients;

    public RemoteGrowthGateway(GrowthProperties properties, ObjectProvider<GrowthHttpClient> clients) {
        this.properties = properties;
        this.clients = clients;
    }

    @Override
    public GrowthSnapshot snapshot(Long userId, List<String> masteryKeys) {
        requireEnabled(userId);
        if (masteryKeys != null && masteryKeys.size() > 500)
            throw new IllegalArgumentException("키워드 조회는 500개씩 나누어 주세요.");

        // 성장의 현재 원본은 LL이다. 조회 요청에는 사용자와 필요한 mastery 범위만 전달한다.
        return clients.getObject().snapshot(userId, masteryKeys);
    }

    @Override
    public List<GrowthActivitySnapshot> activities(Long userId, LearningSource source, LocalDate from, LocalDate to) {
        requireEnabled(userId);

        // LL 페이지의 동일 revision과 커서 전진을 확인해 조회 도중 바뀐 이력을 합치지 않는다.
        List<GrowthActivitySnapshot> result = new ArrayList<>();
        long after = 0;
        String revision = null;
        while (true) {
            var page = clients.getObject().activities(userId, source, from, to, after);
            if (revision != null && !revision.equals(page.projectionRevision()))
                throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                        "GROWTH_READ_CHANGED", "성장 이력이 갱신되어 다시 조회해야 합니다.");
            revision = page.projectionRevision();
            result.addAll(page.activities());
            if (page.nextAfterId() == null) break;
            if (page.nextAfterId() <= after || page.activities().isEmpty())
                throw new IllegalStateException("성장 조회 커서가 전진하지 않았습니다.");
            after = page.nextAfterId();
        }
        return List.copyOf(result);
    }

    private void requireEnabled(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId는 양수여야 합니다.");
        if (!properties.isEnabled() || clients.getIfAvailable() == null)
            throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "LL_GROWTH_DISABLED", "언어학습 성장 데이터 연결이 활성화되지 않았습니다.");
    }
}
