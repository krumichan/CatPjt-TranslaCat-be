package jp.co.translacat.domain.languagelearning.growth.port;

import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;
import java.util.function.Supplier;

public interface GrowthCommands {
    /** 실제 Core 쓰기 트랜잭션에서만 호출한다. 같은 키는 하나의 최종 operation으로 합친다. */
    void stage(long userId, String key, Supplier<GrowthOperation> operation);
    default void append(long userId, GrowthOperation operation) { stage(userId, operation.key(), () -> operation); }
}
