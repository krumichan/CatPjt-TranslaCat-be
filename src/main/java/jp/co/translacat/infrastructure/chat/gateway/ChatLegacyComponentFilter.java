package jp.co.translacat.infrastructure.chat.gateway;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

public final class ChatLegacyComponentFilter implements TypeFilter, EnvironmentAware {
    private boolean gatewayEnabled;

    @Override
    public void setEnvironment(Environment environment) {
        gatewayEnabled = environment.getProperty("chat.gateway.enabled", Boolean.class, false);
    }

    @Override
    public boolean match(MetadataReader metadata, MetadataReaderFactory factory) {
        if (!gatewayEnabled) return false;

        // 정확한 기존 Chat 업무 패키지만 제외한다. 새 core/gateway와 공통 User/LL bean은 제외하지 않는다.
        String name = metadata.getClassMetadata().getClassName();
        return name.startsWith("jp.co.translacat.domain.chat.")
                || name.startsWith("jp.co.translacat.batch.chat.")
                || name.startsWith("jp.co.translacat.infrastructure.redis.presence.")
                || name.startsWith("jp.co.translacat.infrastructure.chat.ai.")
                || name.startsWith("jp.co.translacat.infrastructure.chat.translation.");
    }
}
