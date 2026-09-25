package jp.co.translacat.domain.languagelearning.growth.port;

import jp.co.translacat.domain.languagelearning.growth.model.*;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import java.time.LocalDate;
import java.util.List;

public interface GrowthReadGateway {
    GrowthSnapshot snapshot(Long userId, List<String> masteryKeys);
    default GrowthSnapshot snapshot(Long userId) { return snapshot(userId, null); }
    List<GrowthActivitySnapshot> activities(Long userId, LearningSource source, LocalDate from, LocalDate to);
}
